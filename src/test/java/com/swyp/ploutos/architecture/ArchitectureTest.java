package com.swyp.ploutos.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.swyp.ploutos.PloutosApplication;
import com.swyp.ploutos.architecture.CompiledClasses.ClassInfo;
import com.swyp.ploutos.architecture.CompiledClasses.Reference;

/**
 * 패키지 경계를 테스트로 잠근다. Java 가시성(package-private)이 표현하지 못하는
 * "누가 누구에게 의존해도 되는가"를 여기서 강제한다. 어기면 CI가 실패한다.
 */
class ArchitectureTest {

    private static final String BASE = "com.swyp.ploutos";
    private static final String EXTERNAL = BASE + ".external";
    private static final String COMMON = BASE + ".common";
    private static final String KIS = EXTERNAL + ".kis";
    private static final String KIS_AUTH = KIS + ".auth";
    private static final String JPA_REPOSITORY = "org.springframework.data.jpa.repository.JpaRepository";
    private static final List<String> LAYERS = List.of("repository", "service", "kis", "redis", "controller");

    private static CompiledClasses compiled;

    @BeforeAll
    static void readCompiledClasses() {
        compiled = CompiledClasses.of(PloutosApplication.class);
    }

    @Test
    void 컴파일된_클래스를_읽는다() {
        // when
        List<ClassInfo> classes = compiled.classes();

        // then
        assertThat(classes).isNotEmpty();
        assertThat(classes).extracting(ClassInfo::name).contains(PloutosApplication.class.getName());
    }

    @Test
    void external은_도메인_패키지에_의존하지_않는다() {
        // given
        List<Reference> violations = compiled.references().stream()
                .filter(ref -> inPackage(ref.fromPackage(), EXTERNAL))
                .filter(ref -> inPackage(ref.toPackage(), BASE))
                .filter(ref -> !inPackage(ref.toPackage(), EXTERNAL) && !inPackage(ref.toPackage(), COMMON))
                .toList();

        // then
        assertThat(violations).as(describe(violations)).isEmpty();
    }

    @Test
    void KIS_인증_패키지는_KIS_클라이언트_안에서만_쓴다() {
        // given
        List<Reference> violations = compiled.references().stream()
                .filter(ref -> inPackage(ref.toPackage(), KIS_AUTH))
                .filter(ref -> !inPackage(ref.fromPackage(), KIS))
                .toList();

        // then
        assertThat(violations).as(describe(violations)).isEmpty();
    }

    @Test
    void 리포지토리는_자기_기능_모듈_밖에서_쓰지_않는다() {
        // given
        List<Reference> violations = compiled.references().stream()
                .filter(ref -> isRepository(ref.to()))
                .filter(ref -> !moduleOf(ref.fromPackage()).equals(moduleOf(ref.toPackage())))
                .toList();

        // then
        assertThat(violations).as(describe(violations)).isEmpty();
    }

    @Test
    void 도메인은_계층_패키지에_의존하지_않는다() {
        // given
        List<Reference> violations = compiled.references().stream()
                .filter(ref -> isFeatureModule(ref.fromPackage()) && isFeatureModule(ref.toPackage()))
                .filter(ref -> !isLayerPackage(ref.fromPackage()))
                .filter(ref -> isLayerPackage(ref.toPackage()))
                .toList();

        // then
        assertThat(violations).as(describe(violations)).isEmpty();
    }

    /** 기능 모듈 영역. 인프라(`external`)와 공통(`common`)은 이 레이아웃을 따르지 않는다. */
    private static boolean isFeatureModule(String packageName) {
        return inPackage(packageName, BASE)
                && !inPackage(packageName, EXTERNAL)
                && !inPackage(packageName, COMMON);
    }

    /** 기능 모듈 = 패키지에서 계층 접미어를 뗀 것. `stock.price.service` → `stock.price` */
    private static String moduleOf(String packageName) {
        for (String layer : LAYERS) {
            if (packageName.endsWith("." + layer)) {
                return packageName.substring(0, packageName.length() - layer.length() - 1);
            }
        }
        return packageName;
    }

    private static boolean isLayerPackage(String packageName) {
        return !moduleOf(packageName).equals(packageName);
    }

    private static boolean isRepository(String className) {
        return compiled.classes().stream()
                .filter(info -> info.name().equals(className))
                .anyMatch(info -> info.implementsInterface(JPA_REPOSITORY));
    }

    private static boolean inPackage(String packageName, String prefix) {
        return packageName.equals(prefix) || packageName.startsWith(prefix + ".");
    }

    private static String describe(List<Reference> violations) {
        return violations.stream()
                .map(ref -> ref.from().name() + " -> " + ref.to())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }
}
