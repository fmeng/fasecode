package me.fmeng.fastcode.utils;

import com.google.common.collect.Maps;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.CollectionListModel;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Created by fmeng on 06/08/2017.
 */
public class PsiUtil {
    /***********************常量***********************/
    private PsiUtil() {
    }

    /***********************共有方法***********************/
    public static String getGetterName(String fieldName, PsiClass psiClass) {
        if (StringUtils.isBlank(fieldName) || psiClass == null) {
            return null;
        }
        String matchingGetMethodName = "get" + CodeUtil.firstCharUpperCase(fieldName);
        String matchingIsMethodName = "is" + CodeUtil.firstCharUpperCase(fieldName);
        PsiMethod[] methods = psiClass.getMethods();
        for (PsiMethod iPsiMethod : methods) {
            String iPsiMethodName = iPsiMethod.getName();
            if (matchingGetMethodName.equals(iPsiMethodName)) {
                return matchingGetMethodName;
            } else if (matchingIsMethodName.equals(iPsiMethodName)) {
                return matchingIsMethodName;
            }
        }
        return null;
    }

    public static String getSetterName(String fieldName, PsiClass psiClass) {
        if (StringUtils.isBlank(fieldName) || psiClass == null) {
            return null;
        }
        String matchingSetMethodName = "set" + CodeUtil.firstCharUpperCase(fieldName);
        PsiMethod[] methods = psiClass.getMethods();
        for (PsiMethod iPsiMethod : methods) {
            String iPsiMethodName = iPsiMethod.getName();
            if (matchingSetMethodName.equals(iPsiMethodName)) {
                return matchingSetMethodName;
            }
        }
        return null;
    }

    public static PsiClass getPsiClass(AnActionEvent event) {
        PsiClass psiClass = PsiTreeUtil.getParentOfType(getPsiMethod(event), PsiClass.class);
        return psiClass;
    }

    public static PsiClass getPsiClass(PsiMethod psiMethod) {
        PsiClass psiClass = PsiTreeUtil.getParentOfType(psiMethod, PsiClass.class);
        return psiClass;
    }

    public static PsiClass getPsiClass(PsiType psiType) {
        return psiType instanceof PsiClassType ? ((PsiClassType) psiType).resolve() : null;
    }

    public static PsiMethod getPsiMethod(AnActionEvent e) {
        PsiElement elementAt = getPsiElement(e);
        if (elementAt == null) {
            return null;
        }
        return PsiTreeUtil.getParentOfType(elementAt, PsiMethod.class);
    }

    public static List<PsiField> getPsiFields(PsiClass psiClass) {
        PsiField[] psiFields = psiClass.getFields();
        if (ArrayUtils.isEmpty(psiFields)) {
            return null;
        }
        List<PsiField> srcs = new CollectionListModel<PsiField>(psiFields).getItems();
        List<PsiField> res = new ArrayList<>();
        for (PsiField ipf : srcs) {
            if (isGenercModifierProperty(ipf)) {
                res.add(ipf);
            }
        }
        return res == null || res.size() == 0 ? null : res;
    }

    public static List<PsiField> getPsiFields(PsiType psiType) {
        if (psiType == null
                || psiType.equalsToText("void")) {
            return null;
        }
        String classNameWithPackage = psiType.getInternalCanonicalText();
        Project project = psiType.getResolveScope().getProject();
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        PsiClass psiClass = facade.findClass(classNameWithPackage, GlobalSearchScope.allScope(project));
        if (psiClass == null) {
            return null;
        }
        return getPsiFields(psiClass);
    }

    public static PsiClass getReturnType(PsiMethod psiMethod) {
        if (psiMethod == null) {
            return null;
        }
        return getPsiClass(psiMethod.getReturnType());
    }

    public static String getMethodName(PsiMethod psiMethod) {
        if (psiMethod == null) {
            return null;
        }
        return psiMethod.getName();
    }

    /**
     * 返回有序的map
     */
    public static TreeMap<String, PsiClass> getParams(PsiMethod psiMethod) {
        if (psiMethod == null
                || psiMethod.getParameterList() == null
                || psiMethod.getParameterList().getParameters() == null
                || psiMethod.getParameterList().getParameters().length < 1) {
            return null;
        }
        TreeMap<String, PsiClass> params = new TreeMap<>();
        PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
        for (PsiParameter p : parameters) {
            if (p != null) {
                String name = p.getName();
                String text = p.getText();
                // 兼容数组类型的参数
                PsiClass type;
                if (text.contains("[]") || text.contains("...")) {
                    type = PsiElementFactory.SERVICE.getInstance(psiMethod.getProject()).getArrayClass(LanguageLevel.JDK_1_8);
                } else {
                    type = getPsiClass(p.getType());
                }
                params.put(name, type);
            }
        }
        return params;
    }

    /**
     * @param psiClass
     * @param candidateMap
     */
    public static void ensureImport(PsiClass psiClass, SortedMap<String, String> candidateMap) {
        if (psiClass == null) {
            return;
        }
        PsiFile file = psiClass.getContainingFile();
        if (!(file instanceof PsiJavaFile)) {
            return;
        }
        final Project project = psiClass.getProject();
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        final PsiElementFactory elementFactory =
                JavaPsiFacade.getInstance(project).getElementFactory();

        // 1. 去重复
        // candidateMap--> <"com.google...PreConditions", "PreConditions">
        // reverseMap  --> <"PreConditions", "com.google...PreConditions">
        SortedMap<String, String> reverseMap = Maps.newTreeMap();
        candidateMap.forEach((kClass, vName) -> {
            PsiClass findClass = facade.findClass(kClass, GlobalSearchScope.allScope(project));
            if (findClass != null) {
                reverseMap.put(vName, kClass);
            }
        });

        // 2. 优化导入
        for (String ishortName : reverseMap.keySet()) {
            if (wordContains(psiClass, ishortName)) {
                PsiClass importClass = facade.findClass(reverseMap.get(ishortName), GlobalSearchScope.allScope(project));
                if (importClass != null) {
                    ((PsiJavaFile) file).importClass(importClass);
                }
            }
        }
    }


    /***********************私有方法***********************/

    private static PsiElement getPsiElement(AnActionEvent e) {
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        if (psiFile == null || editor == null) {
            e.getPresentation().setEnabled(false);
            return null;
        }
        //用来获取当前光标处的PsiElement
        int offset = editor.getCaretModel().getOffset();
        return psiFile.findElementAt(offset);
    }

    private static boolean isGenercModifierProperty(PsiField psiField) {
        PsiModifierList modifierList = psiField.getModifierList();
        if (modifierList == null
                || modifierList.hasModifierProperty(PsiModifier.STATIC)
                || modifierList.hasModifierProperty(PsiModifier.FINAL)
                || modifierList.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
            return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }

    private static boolean wordContains(PsiClass psiClass, String word) {
        return psiClass.getText().contains(word);
    }

    public static void main(String[] args) {

    }
}
