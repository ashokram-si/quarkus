package io.quarkus.maven.config.doc;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.annotation.processor.documentation.config.model.AbstractConfigItem;
import io.quarkus.annotation.processor.documentation.config.model.ConfigPhase;
import io.quarkus.annotation.processor.documentation.config.model.ConfigProperty;
import io.quarkus.annotation.processor.documentation.config.model.ConfigProperty.PropertyPath;
import io.quarkus.annotation.processor.documentation.config.model.ConfigRoot;
import io.quarkus.annotation.processor.documentation.config.model.ConfigSection;
import io.quarkus.annotation.processor.documentation.config.model.ConfigSection.SectionPath;
import io.quarkus.annotation.processor.documentation.config.model.Deprecation;
import io.quarkus.annotation.processor.documentation.config.model.Extension;
import io.quarkus.annotation.processor.documentation.config.model.SourceElementType;
import io.quarkus.annotation.processor.documentation.config.util.Types;
import io.quarkus.maven.config.doc.generator.Format;

class GenerateConfigDocMojoTest {

    @Test
    void findsTargetDirectoryOfARealModule(@TempDir Path root) throws IOException, MojoExecutionException {
        Path moduleTarget = createModuleWithTarget(root, "module-a");

        List<Path> found = GenerateConfigDocMojo.findTargetDirectories(root);

        assertThat(found).containsExactly(moduleTarget);
    }

    @Test
    void scanRootBeingAGitCheckoutDoesNotHideItsOwnModules(@TempDir Path root)
            throws IOException, MojoExecutionException {
        // the scan root is normally the project's own repo root, which of course has a .git directory;
        // that must not cause the whole scan to be treated as "nested" and skipped
        Files.createDirectories(root.resolve(".git"));
        Path moduleTarget = createModuleWithTarget(root, "module-a");

        List<Path> found = GenerateConfigDocMojo.findTargetDirectories(root);

        assertThat(found).containsExactly(moduleTarget);
    }

    @Test
    void ignoresTargetDirectoriesInsideNestedGitCheckouts(@TempDir Path root)
            throws IOException, MojoExecutionException {
        Path realModuleTarget = createModuleWithTarget(root, "module-a");

        // e.g. a git worktree for another branch, kept inside the project directory,
        // that still has stale build output from a previous build
        Path nestedCheckout = root.resolve(".claude").resolve("worktrees").resolve("stale-branch");
        Files.createDirectories(nestedCheckout.resolve(".git"));
        createModuleWithTarget(nestedCheckout, "module-a");

        List<Path> found = GenerateConfigDocMojo.findTargetDirectories(root);

        assertThat(found).containsExactly(realModuleTarget);
    }

    @Test
    void ignoresNestedGitCheckoutMarkedByAGitFile(@TempDir Path root) throws IOException, MojoExecutionException {
        // git worktrees use a `.git` *file* (not a directory) pointing at the real git dir
        Path realModuleTarget = createModuleWithTarget(root, "module-a");

        Path worktree = root.resolve(".claude").resolve("worktrees").resolve("issue-1234");
        Files.createDirectories(worktree);
        Files.writeString(worktree.resolve(".git"), "gitdir: /somewhere/else\n");
        createModuleWithTarget(worktree, "module-a");

        List<Path> found = GenerateConfigDocMojo.findTargetDirectories(root);

        assertThat(found).containsExactly(realModuleTarget);
    }

    @Test
    void ignoresTargetDirectoriesDeeplyNestedInsideAGitCheckout(@TempDir Path root)
            throws IOException, MojoExecutionException {
        // mirrors the real-world case: the .git marker is several directories above the
        // polluting target/ dir, e.g. .claude/worktrees/<branch>/model-providers/<ext>/runtime/target
        Path realModuleTarget = createModuleWithTarget(root, "module-a");

        Path worktreeRoot = root.resolve(".claude").resolve("worktrees").resolve("issue-2221");
        Files.createDirectories(worktreeRoot.resolve(".git"));
        Path deeplyNestedModuleParent = worktreeRoot.resolve("model-providers").resolve("openai")
                .resolve("openai-vanilla");
        createModuleWithTarget(deeplyNestedModuleParent, "runtime");

        List<Path> found = GenerateConfigDocMojo.findTargetDirectories(root);

        assertThat(found).containsExactly(realModuleTarget);
    }

    private static Path createModuleWithTarget(Path parent, String moduleName) throws IOException {
        Path moduleDir = parent.resolve(moduleName);
        Path target = moduleDir.resolve("target");
        Files.createDirectories(target);
        Files.createFile(moduleDir.resolve("pom.xml"));
        return target;
    }

    @Test
    void asciidocAddsTheDurationNoteForDeprecatedTopLevelProperties() {
        // The AsciiDoc table lists the deprecated properties of the root, and each row links to the note (#58504).
        ConfigRoot onlyDeprecated = root(property("quarkus.test.timeout", DURATION, true));
        assertThat(GenerateConfigDocMojo.includeDurationNote(onlyDeprecated, Format.asciidoc)).isTrue();
        // The Markdown table leaves them out.
        assertThat(GenerateConfigDocMojo.includeDurationNote(onlyDeprecated, Format.markdown)).isFalse();

        ConfigRoot current = root(property("quarkus.test.timeout", DURATION, false));
        assertThat(GenerateConfigDocMojo.includeDurationNote(current, Format.asciidoc)).isTrue();
        assertThat(GenerateConfigDocMojo.includeDurationNote(current, Format.markdown)).isTrue();
    }

    @Test
    void asciidocAddsTheMemorySizeNoteForDeprecatedTopLevelProperties() {
        ConfigRoot onlyDeprecated = root(property("quarkus.test.buffer", Types.MEMORY_SIZE_TYPE, true));
        assertThat(GenerateConfigDocMojo.includeMemorySizeNote(onlyDeprecated, Format.asciidoc)).isTrue();
        assertThat(GenerateConfigDocMojo.includeMemorySizeNote(onlyDeprecated, Format.markdown)).isFalse();
    }

    @Test
    void nestedSectionsCountOnlyTheirNonDeprecatedProperties() {
        // The section tag shows only the non-deprecated items, in both formats.
        ConfigSection section = section("quarkus.test.section");
        section.addItem(property("quarkus.test.section.timeout", DURATION, true));
        ConfigRoot inSection = root(section);
        assertThat(GenerateConfigDocMojo.includeDurationNote(inSection, Format.asciidoc)).isFalse();
        assertThat(GenerateConfigDocMojo.includeDurationNote(inSection, Format.markdown)).isFalse();
    }

    @Test
    void aGeneratedSectionCountsItsOwnDeprecatedProperties() {
        // A generated section file lists the section's own items, deprecated ones included, in AsciiDoc.
        ConfigSection generated = section("quarkus.test.section");
        generated.addItem(property("quarkus.test.section.timeout", DURATION, true));
        assertThat(GenerateConfigDocMojo.includeDurationNote(generated, Format.asciidoc)).isTrue();
        assertThat(GenerateConfigDocMojo.includeDurationNote(generated, Format.markdown)).isFalse();
    }

    private static final String DURATION = Duration.class.getName();

    private static ConfigRoot root(AbstractConfigItem item) {
        ConfigRoot root = new ConfigRoot(Extension.createNotDetected(), "quarkus.test", null, null);
        root.addItem(item);
        return root;
    }

    private static ConfigSection section(String path) {
        return new ConfigSection("io.quarkus.Test", "section", SourceElementType.METHOD, new SectionPath(path),
                "io.quarkus.Section", 1, false, null);
    }

    private static ConfigProperty property(String path, String type, boolean deprecated) {
        return new ConfigProperty(ConfigPhase.RUN_TIME, "io.quarkus.Test", "property", SourceElementType.METHOD,
                new PropertyPath(path, "QUARKUS_TEST_PROPERTY"), List.of(), type, null, false, false, true, false,
                null, false, false, false, false, null, null, false, null,
                deprecated ? new Deprecation(null, null) : null);
    }
}
