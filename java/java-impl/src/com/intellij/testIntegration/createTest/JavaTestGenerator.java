/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testIntegration.createTest;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testIntegration.TestFramework;
import com.intellij.testIntegration.TestIntegrationUtils;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public class JavaTestGenerator implements TestGenerator {
  public JavaTestGenerator() {
  }

  public PsiElement generateTest(final Project project, final CreateTestDialog d) {
    return PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(new Computable<PsiElement>() {
      public PsiElement compute() {
        return ApplicationManager.getApplication().runWriteAction(new Computable<PsiElement>() {
          public PsiElement compute() {
            try {
              IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

              PsiClass targetClass = createTestClass(d);
              addSuperClass(targetClass, project, d.getSuperClassName());

              Editor editor = CodeInsightUtil.positionCursor(project, targetClass.getContainingFile(), targetClass.getLBrace());
              addTestMethods(editor,
                             targetClass,
                             d.getSelectedTestFrameworkDescriptor(),
                             d.getSelectedMethods(),
                             d.shouldGeneratedBefore(),
                             d.shouldGeneratedAfter());
              return targetClass;
            }
            catch (IncorrectOperationException e) {
              showErrorLater(project, d.getClassName());
              return null;
            }
          }
        });
      }
    });
  }

  private static PsiClass createTestClass(CreateTestDialog d) {
    final TestFramework testFrameworkDescriptor = d.getSelectedTestFrameworkDescriptor();
    final FileTemplateDescriptor fileTemplateDescriptor = TestIntegrationUtils.MethodKind.TEST_CLASS.getFileTemplateDescriptor(testFrameworkDescriptor);
    final PsiDirectory targetDirectory = d.getTargetDirectory();
    if (fileTemplateDescriptor != null) {
      final PsiClass classFromTemplate = createTestClassFromCodeTemplate(d, fileTemplateDescriptor, targetDirectory);
      if (classFromTemplate != null) {
        return classFromTemplate;
      }
    }

    return JavaDirectoryService.getInstance().createClass(targetDirectory, d.getClassName());
  }

  private static PsiClass createTestClassFromCodeTemplate(final CreateTestDialog d,
                                                          final FileTemplateDescriptor fileTemplateDescriptor,
                                                          final PsiDirectory targetDirectory) {
    final String templateName = fileTemplateDescriptor.getFileName();
    final FileTemplate fileTemplate = FileTemplateManager.getInstance().getCodeTemplate(templateName);
    final Properties defaultProperties = FileTemplateManager.getInstance().getDefaultProperties(targetDirectory.getProject());
    Properties properties = new Properties(defaultProperties);
    properties.setProperty(FileTemplate.ATTRIBUTE_NAME, d.getClassName());
    try {
      final PsiElement psiElement = FileTemplateUtil.createFromTemplate(fileTemplate, templateName, properties, targetDirectory);
      if (psiElement instanceof PsiClass) {
        return (PsiClass)psiElement;
      }
      return null;
    }
    catch (Exception e) {
      return null;
    }
  }

  private static void addSuperClass(PsiClass targetClass, Project project, String superClassName) throws IncorrectOperationException {
    if (superClassName == null) return;
    final PsiReferenceList extendsList = targetClass.getExtendsList();
    if (extendsList == null || extendsList.getReferencedTypes().length > 0) return;

    PsiElementFactory ef = JavaPsiFacade.getInstance(project).getElementFactory();
    PsiJavaCodeReferenceElement superClassRef;

    PsiClass superClass = findClass(project, superClassName);
    if (superClass != null) {
      superClassRef = ef.createClassReferenceElement(superClass);
    }
    else {
      superClassRef = ef.createFQClassNameReferenceElement(superClassName, GlobalSearchScope.allScope(project));
    }
    extendsList.add(superClassRef);
  }

  @Nullable
  private static PsiClass findClass(Project project, String fqName) {
    GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    return JavaPsiFacade.getInstance(project).findClass(fqName, scope);
  }

  private static void addTestMethods(Editor editor,
                                     PsiClass targetClass,
                                     TestFramework descriptor,
                                     Collection<MemberInfo> methods,
                                     boolean generateBefore,
                                     boolean generateAfter) throws IncorrectOperationException {
    final Set<String> existingNames = new HashSet<String>();
    if (generateBefore) {
      generateMethod(TestIntegrationUtils.MethodKind.SET_UP, descriptor, targetClass, editor, null, existingNames);
    }
    if (generateAfter) {
      generateMethod(TestIntegrationUtils.MethodKind.TEAR_DOWN, descriptor, targetClass, editor, null, existingNames);
    }
    for (MemberInfo m : methods) {
      generateMethod(TestIntegrationUtils.MethodKind.TEST, descriptor, targetClass, editor, m.getMember().getName(), existingNames);
    }
  }

  private static void showErrorLater(final Project project, final String targetClassName) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        Messages.showErrorDialog(project,
                                 CodeInsightBundle.message("intention.error.cannot.create.class.message", targetClassName),
                                 CodeInsightBundle.message("intention.error.cannot.create.class.title"));
      }
    });
  }

  private static void generateMethod(TestIntegrationUtils.MethodKind methodKind,
                                     TestFramework descriptor,
                                     PsiClass targetClass,
                                     Editor editor,
                                     @Nullable String name, Set<String> existingNames) {
    PsiMethod method = (PsiMethod)targetClass.add(TestIntegrationUtils.createDummyMethod(targetClass));
    PsiDocumentManager.getInstance(targetClass.getProject()).doPostponedOperationsAndUnblockDocument(editor.getDocument());
    TestIntegrationUtils.runTestMethodTemplate(methodKind, descriptor, editor, targetClass, method, name, true, existingNames);
  }

  @Override
  public String toString() {
    return CodeInsightBundle.message("intention.create.test.dialog.java");
  }
}