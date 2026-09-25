package com.swyp.ploutos.architecture;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 컴파일된 메인 클래스들이 서로 어떤 클래스를 참조하는지 읽는다.
 * ArchUnit이 Java 26 클래스 파일을 아직 읽지 못해, JDK 표준 ClassFile API로 같은 정보를 얻는다.
 * 참조는 상수 풀의 클래스 항목과 디스크립터·시그니처에 등장하는 타입에서 뽑는다.
 */
final class CompiledClasses {

    record ClassInfo(String name, Set<String> interfaces, Set<String> references) {

        String packageName() {
            return packageOf(name);
        }

        boolean implementsInterface(String interfaceName) {
            return interfaces.contains(interfaceName);
        }
    }

    record Reference(ClassInfo from, String to) {

        String fromPackage() {
            return from.packageName();
        }

        String toPackage() {
            return packageOf(to);
        }
    }

    private static final Pattern TYPE_IN_DESCRIPTOR = Pattern.compile("L([\\w/$]+);");

    private final List<ClassInfo> classes;

    private CompiledClasses(List<ClassInfo> classes) {
        this.classes = classes;
    }

    static CompiledClasses of(Class<?> anchor) {
        try {
            Path root = Path.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
            try (Stream<Path> files = Files.walk(root)) {
                List<ClassInfo> classes = files
                        .filter(path -> path.toString().endsWith(".class"))
                        .map(CompiledClasses::read)
                        .toList();
                return new CompiledClasses(classes);
            }
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("컴파일된 클래스를 읽을 수 없습니다", e);
        }
    }

    List<ClassInfo> classes() {
        return classes;
    }

    /** 프로젝트 클래스 사이의 참조만 돌려준다. 자기 자신에 대한 참조는 제외한다. */
    List<Reference> references() {
        Set<String> projectClasses = new TreeSet<>(classes.stream().map(ClassInfo::name).toList());
        return classes.stream()
                .flatMap(from -> from.references().stream()
                        .filter(projectClasses::contains)
                        .filter(to -> !to.equals(from.name()))
                        .map(to -> new Reference(from, to)))
                .toList();
    }

    static String packageOf(String className) {
        int lastDot = className.lastIndexOf('.');
        if (lastDot < 0) {
            return "";
        }
        return className.substring(0, lastDot);
    }

    private static ClassInfo read(Path path) {
        try {
            ClassModel model = ClassFile.of().parse(Files.readAllBytes(path));
            Set<String> interfaces = new TreeSet<>();
            model.interfaces().forEach(entry -> interfaces.add(toClassName(entry.asInternalName())));
            Set<String> references = new TreeSet<>();
            for (PoolEntry entry : model.constantPool()) {
                collect(entry, references);
            }
            return new ClassInfo(toClassName(model.thisClass().asInternalName()), interfaces, references);
        } catch (IOException e) {
            throw new IllegalStateException("클래스 파일을 읽을 수 없습니다: " + path, e);
        }
    }

    private static void collect(PoolEntry entry, Set<String> references) {
        if (entry instanceof ClassEntry classEntry) {
            references.add(toClassName(stripArray(classEntry.asInternalName())));
            return;
        }
        if (entry instanceof Utf8Entry utf8) {
            Matcher matcher = TYPE_IN_DESCRIPTOR.matcher(utf8.stringValue());
            while (matcher.find()) {
                references.add(toClassName(matcher.group(1)));
            }
        }
    }

    private static String stripArray(String internalName) {
        String name = internalName;
        while (name.startsWith("[")) {
            name = name.substring(1);
        }
        if (name.startsWith("L") && name.endsWith(";")) {
            return name.substring(1, name.length() - 1);
        }
        return name;
    }

    private static String toClassName(String internalName) {
        return internalName.replace('/', '.');
    }
}
