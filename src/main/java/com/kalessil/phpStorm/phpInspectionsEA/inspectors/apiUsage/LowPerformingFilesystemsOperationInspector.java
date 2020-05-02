package com.kalessil.phpStorm.phpInspectionsEA.inspectors.apiUsage;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.inspections.PhpInspection;
import com.jetbrains.php.lang.psi.elements.*;
import com.kalessil.phpStorm.phpInspectionsEA.fixers.UseSuggestedReplacementFixer;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.FeaturedPhpElementVisitor;
import com.kalessil.phpStorm.phpInspectionsEA.settings.StrictnessCategory;
import com.kalessil.phpStorm.phpInspectionsEA.utils.MessagesPresentationUtil;
import com.kalessil.phpStorm.phpInspectionsEA.utils.OpenapiTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/*
 * This file is part of the Php Inspections (EA Extended) package.
 *
 * (c) Vladimir Reznichenko <kalessil@gmail.com>
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */

public class LowPerformingFilesystemsOperationInspector extends PhpInspection {
    private static final String messageSortsByDefaultPattern = "'%s(...)' sorts results by default, please provide second argument for specifying the intention.";
    private static final String messageUnboxGlobPattern      = "'%s' would be more performing here (reduces amount of file system interactions).";
    private static final String messageFileExistsPattern     = "'%s' would be more performing here (uses builtin caches).";

    private static final Set<String> filesRelatedNaming       = new HashSet<>();
    private static final Set<String> directoriesRelatedNaming = new HashSet<>();
    static {
        directoriesRelatedNaming.add("dir");
        directoriesRelatedNaming.add("directory");
        directoriesRelatedNaming.add("folder");
        directoriesRelatedNaming.add("dirname");

        filesRelatedNaming.add("file");
        filesRelatedNaming.add("filename");
        filesRelatedNaming.add("image");
        filesRelatedNaming.add("img");
        filesRelatedNaming.add("picture");
        filesRelatedNaming.add("pic");
        filesRelatedNaming.add("thumbnail");
        filesRelatedNaming.add("thumb");
    }

    @NotNull
    @Override
    public String getShortName() {
        return "LowPerformingFilesystemsOperationInspection";
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return "Low performing filesystem operations";
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new FeaturedPhpElementVisitor() {
            @Override
            public void visitPhpFunctionCall(@NotNull FunctionReference reference) {
                if (this.shouldSkipAnalysis(reference, StrictnessCategory.STRICTNESS_CATEGORY_PERFORMANCE)) { return; }

                final String functionName = reference.getName();
                if (functionName != null && (functionName.equals("scandir") || functionName.equals("glob"))) {
                    final PsiElement[] arguments = reference.getParameters();

                    /* case: glob call results passed thru is_dir */
                    if (functionName.equals("glob")) {
                        final PsiElement parent  = reference.getParent();
                        final PsiElement context = parent instanceof ParameterList ? parent.getParent() : parent;
                        if (OpenapiTypesUtil.isFunctionReference(context)) {
                            final FunctionReference outerCall = (FunctionReference) context;
                            final String outerFunctionName    = outerCall.getName();
                            if (outerFunctionName != null && outerFunctionName.equals("array_filter")) {
                                final PsiElement[] outerArguments = outerCall.getParameters();
                                if (outerArguments.length == 2 && outerArguments[1] instanceof StringLiteralExpression) {
                                    final String callback = ((StringLiteralExpression) outerArguments[1]).getContents().replace("\\", "");
                                    if (callback.equals("is_dir")) {
                                        final boolean needsFilter = PsiTreeUtil.findChildrenOfType(reference, ConstantReference.class).stream().noneMatch(c -> "GLOB_ONLYDIR".equals(c.getName()));
                                        final String replacement  = String.format(
                                                "%sglob(%s, %s)",
                                                reference.getImmediateNamespaceName(),
                                                arguments[0].getText(),
                                                needsFilter ? (arguments.length == 2 ? arguments[1].getText() + " | GLOB_ONLYDIR" : "GLOB_ONLYDIR") : arguments[1].getText()
                                        );
                                        holder.registerProblem(
                                                outerCall,
                                                String.format(MessagesPresentationUtil.prefixWithEa(messageUnboxGlobPattern), replacement),
                                                new OptimizeDirectoriesFilteringFix(replacement)
                                        );
                                        return;
                                    }
                                }
                            }
                        }
                    }

                    /* case: sorting expectations are not clarified */
                    if (arguments.length == 1 && this.isFromRootNamespace(reference)) {
                        final String replacement = String.format(
                                "%s%s(%s, %s)",
                                reference.getImmediateNamespaceName(),
                                functionName,
                                arguments[0].getText(),
                                functionName.equals("scandir") ? "SCANDIR_SORT_NONE" : "GLOB_NOSORT"
                        );
                        holder.registerProblem(
                                reference,
                                String.format(MessagesPresentationUtil.prefixWithEa(messageSortsByDefaultPattern), functionName),
                                new NoSortFix(replacement)
                        );
                        return;
                    }
                }

                if (functionName != null && functionName.equals("file_exists")) {
                    final PsiElement[] arguments = reference.getParameters();
                    if (arguments.length == 1) {
                        /* strategy 1: guess by subject name (clean coders will benefit in performance) */
                        final String stringToGuess = this.extractNameToGuess(arguments[0]);
                        if (stringToGuess != null) {
                            final LocalQuickFix fixer;
                            final String alternative;
                            if (filesRelatedNaming.contains(stringToGuess)) {
                                alternative = "is_file";
                                fixer       = new UseIsFileInsteadFixer();
                            } else if (directoriesRelatedNaming.contains(stringToGuess)) {
                                alternative = "is_dir";
                                fixer       = new UseIsDirInsteadFixer();
                            } else {
                                alternative = null;
                                fixer       = null;
                            }
                            if (alternative != null && this.isFromRootNamespace(reference)) {
                                final String replacement = String.format(
                                        "%s%s(%s)",
                                        reference.getImmediateNamespaceName(),
                                        alternative,
                                        arguments[0].getText()
                                );
                                holder.registerProblem(
                                        reference,
                                        String.format(MessagesPresentationUtil.prefixWithEa(messageFileExistsPattern), replacement),
                                        fixer
                                );
                                return;
                            }
                        }
                    }
                    /*
                        strategy 2: scan argument usages (slow strategy)
                            - file_exists + file_get_contents|file_put_contents|unlink|filesize|file|fopen -> is_file
                            - file_exists + mkdir|rmdir|glob|scandir -> is_dir
                        special:
                            - file_exists($file) '&&'|'||' is_file|is_dir|is_link($file): file_exists on left/right is not needed at all
                     */
                }
            }

            @Nullable
            private String extractNameToGuess(@NotNull PsiElement subject) {
                /* the subject can be hidden in assignment and array access expressions */
                if (OpenapiTypesUtil.isAssignment(subject)) {
                    subject = ((AssignmentExpression) subject).getVariable();
                }
                if (subject instanceof ArrayAccessExpression) {
                    final ArrayIndex index = ((ArrayAccessExpression) subject).getIndex();
                    if (index != null) {
                        subject = index.getValue();
                    }
                }
                String stringToGuess = null;
                if (subject instanceof Variable || subject instanceof FieldReference || subject instanceof FunctionReference) {
                    stringToGuess = ((PhpReference) subject).getName();
                } else if (subject instanceof StringLiteralExpression) {
                    final StringLiteralExpression literal = (StringLiteralExpression) subject;
                    stringToGuess = literal.getFirstPsiChild() == null ? literal.getContents() : null;
                }
                return stringToGuess == null || stringToGuess.isEmpty() ? null : stringToGuess.toLowerCase();
            }
        };
    }

    private static final class NoSortFix extends UseSuggestedReplacementFixer {
        private static final String title = "Disable sorting by default";

        @NotNull
        @Override
        public String getName() {
            return MessagesPresentationUtil.prefixWithEa(title);
        }

        NoSortFix(@NotNull String expression) {
            super(expression);
        }
    }

    private static final class OptimizeDirectoriesFilteringFix extends UseSuggestedReplacementFixer {
        private static final String title = "Optimize directories filtering";

        @NotNull
        @Override
        public String getName() {
            return MessagesPresentationUtil.prefixWithEa(title);
        }

        OptimizeDirectoriesFilteringFix(@NotNull String expression) {
            super(expression);
        }
    }

    private static final class UseIsFileInsteadFixer implements LocalQuickFix {
        private static final String title = "Use 'is_file()' instead";

        @NotNull
        @Override
        public String getName() {
            return MessagesPresentationUtil.prefixWithEa(title);
        }

        @NotNull
        @Override
        public String getFamilyName() {
            return getName();
        }

        UseIsFileInsteadFixer() {
            super();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            final PsiElement reference = descriptor.getPsiElement();
            if (reference instanceof FunctionReference && !project.isDisposed()) {
                ((FunctionReference) reference).handleElementRename("is_file");
            }
        }
    }

    private static final class UseIsDirInsteadFixer implements LocalQuickFix {
        private static final String title = "Use 'is_dir()' instead";

        @NotNull
        @Override
        public String getName() {
            return MessagesPresentationUtil.prefixWithEa(title);
        }

        @NotNull
        @Override
        public String getFamilyName() {
            return getName();
        }

        UseIsDirInsteadFixer() {
            super();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            final PsiElement reference = descriptor.getPsiElement();
            if (reference instanceof FunctionReference && !project.isDisposed()) {
                ((FunctionReference) reference).handleElementRename("is_dir");
            }
        }
    }
}
