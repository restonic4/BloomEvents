package com.restonic4.bloom.events.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Annotation processor that generates event dispatchers for {@link Event} interfaces.
 */
@SupportedAnnotationTypes("com.restonic4.bloom.events.processor.Event")
public class EventProcessor extends AbstractProcessor {
    private static final String EVENT_CLASS = "com.restonic4.bloom.events.Event";
    private static final String FACTORY_CLASS = "com.restonic4.bloom.events.EventFactory";
    private static final String RESULT_CLASS = "com.restonic4.bloom.events.EventResult";

    private Messager messager;
    private Filer filer;
    private Elements elements;
    private Types types;

    private final Set<String> generated = new HashSet<>();

    private record EventDecl(TypeElement type, String canonicalName, String fieldName, String methodName, int paramCount, boolean cancellable) { }

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        this.messager = env.getMessager();
        this.filer = env.getFiler();
        this.elements = env.getElementUtils();
        this.types = env.getTypeUtils();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        Map<TypeElement, List<EventDecl>> byContainer = new LinkedHashMap<>();

        for (Element element : round.getElementsAnnotatedWith(Event.class)) {
            if (element.getKind() != ElementKind.INTERFACE) {
                error(element, "@Event can only be applied to interfaces.");
                continue;
            }
            TypeElement type = (TypeElement) element;
            EventDecl decl = analyze(type);
            if (decl == null) continue;
            byContainer.computeIfAbsent(containerOf(type), k -> new ArrayList<>()).add(decl);
        }

        byContainer.forEach(this::generate);
        return false; // don't claim the annotation
    }

    private EventDecl analyze(TypeElement type) {
        if (!type.getTypeParameters().isEmpty()) {
            error(type, "@Event interfaces cannot have type parameters.");
            return null;
        }

        for (Element e = type; e instanceof TypeElement; e = e.getEnclosingElement()) {
            if (e.getModifiers().contains(Modifier.PRIVATE)) {
                error(type, "@Event interface must not be private (or nested in a private type), " + "the generated class could not access it.");
                return null;
            }
        }

        List<ExecutableElement> abstractMethods = new ArrayList<>();
        for (Element member : elements.getAllMembers(type)) {
            if (member.getKind() == ElementKind.METHOD && member.getModifiers().contains(Modifier.ABSTRACT)) {
                abstractMethods.add((ExecutableElement) member);
            }
        }
        if (abstractMethods.size() != 1) {
            error(type, "@Event interface must declare exactly one abstract method, found " + abstractMethods.size() + ".");
            return null;
        }

        ExecutableElement method = abstractMethods.get(0);
        if (!method.getTypeParameters().isEmpty()) {
            error(method, "@Event method cannot be generic.");
            return null;
        }

        boolean cancellable = type.getAnnotation(Event.class).cancellable();
        TypeMirror returnType = method.getReturnType();

        if (cancellable && !isEventResult(returnType)) {
            error(method, "Cancellable events must return " + RESULT_CLASS + ".");
            return null;
        }
        if (!cancellable && returnType.getKind() != TypeKind.VOID) {
            error(method, "Non-cancellable events must return void. " + "Use @Event(cancellable = true) to return " + RESULT_CLASS + ".");
            return null;
        }

        return new EventDecl(
                type,
                type.getQualifiedName().toString(),
                toConstantName(type.getSimpleName().toString()),
                method.getSimpleName().toString(),
                method.getParameters().size(),
                cancellable
        );
    }

    private boolean isEventResult(TypeMirror mirror) {
        if (mirror.getKind() != TypeKind.DECLARED) return false;
        Element e = types.asElement(mirror);
        return e instanceof TypeElement t && t.getQualifiedName().contentEquals(RESULT_CLASS);
    }

    private static TypeElement containerOf(TypeElement type) {
        Element enclosing = type.getEnclosingElement();
        return enclosing instanceof TypeElement t ? t : type;
    }

    private void generate(TypeElement container, List<EventDecl> decls) {
        Set<String> seen = new HashSet<>();
        for (EventDecl d : decls) {
            if (!seen.add(d.fieldName())) {
                error(d.type(), "Another @Event in " + container.getQualifiedName() + " also maps to the field name " + d.fieldName() + ".");
                return;
            }
        }

        String pkg = elements.getPackageOf(container).getQualifiedName().toString();
        String chain = chainName(container);
        boolean baseMode = extendsGeneratedBase(container, chain + "Base");
        String className = chain + (baseMode ? "Base" : "Events");
        String qualified = pkg.isEmpty() ? className : pkg + "." + className;

        if (!generated.add(qualified)) return;

        Element[] originating = decls.stream().map(EventDecl::type).toArray(Element[]::new);
        try {
            JavaFileObject file = filer.createSourceFile(qualified, originating);
            try (Writer w = file.openWriter()) {
                w.write(render(pkg, className, baseMode, decls));
            }
        } catch (IOException ex) {
            error(container, "Could not generate " + qualified + ": " + ex.getMessage());
        }
    }

    private boolean extendsGeneratedBase(TypeElement container, String expectedSimpleName) {
        if (container.getKind() != ElementKind.CLASS) return false;
        TypeMirror superclass = container.getSuperclass();
        if (superclass.getKind() != TypeKind.DECLARED && superclass.getKind() != TypeKind.ERROR) return false;
        Element superElement = types.asElement(superclass);
        return superElement != null && superElement.getSimpleName().contentEquals(expectedSimpleName);
    }

    private String render(String pkg, String className, boolean baseMode, List<EventDecl> decls) {
        StringBuilder sb = new StringBuilder();
        if (!pkg.isEmpty()) {
            sb.append("package ").append(pkg).append(";\n\n");
        }
        sb.append("@javax.annotation.processing.Generated(\"").append(EventProcessor.class.getName()).append("\")\n");

        if (baseMode) {
            sb.append("public abstract class ").append(className).append(" {\n");
            sb.append("    protected ").append(className).append("() {}\n\n");
        } else {
            sb.append("public final class ").append(className).append(" {\n");
            sb.append("    private ").append(className).append("() {}\n\n");
        }

        for (EventDecl d : decls) {
            sb.append(renderField(d)).append("\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private String renderField(EventDecl d) {
        String type = d.canonicalName();

        StringBuilder params = new StringBuilder();
        StringBuilder args = new StringBuilder();
        for (int i = 0; i < d.paramCount(); i++) {
            if (i > 0) {
                params.append(", ");
                args.append(", ");
            }
            params.append("p").append(i);
            args.append("p").append(i);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("    public static final ").append(EVENT_CLASS).append("<").append(type).append("> ")
                .append(d.fieldName()).append(" =\n");
        sb.append("            ").append(FACTORY_CLASS).append(".createArray(").append(type)
                .append(".class, listeners -> (").append(params).append(") -> {\n");
        sb.append("                for (").append(type).append(" listener : listeners) {\n");

        if (d.cancellable()) {
            sb.append("                    ").append(RESULT_CLASS).append(" result = listener.")
                    .append(d.methodName()).append("(").append(args).append(");\n");
            sb.append("                    if (result != ").append(RESULT_CLASS).append(".CONTINUE) {\n");
            sb.append("                        return result;\n");
            sb.append("                    }\n");
            sb.append("                }\n");
            sb.append("                return ").append(RESULT_CLASS).append(".CONTINUE;\n");
        } else {
            sb.append("                    listener.").append(d.methodName()).append("(").append(args).append(");\n");
            sb.append("                }\n");
        }

        sb.append("            });\n");
        return sb.toString();
    }

    private static String chainName(TypeElement type) {
        Deque<String> parts = new ArrayDeque<>();
        for (Element e = type; e instanceof TypeElement; e = e.getEnclosingElement()) {
            parts.addFirst(e.getSimpleName().toString());
        }
        return String.join("", parts);
    }

    static String toConstantName(String name) {
        return name
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toUpperCase(Locale.ROOT);
    }

    private void error(Element element, String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }
}