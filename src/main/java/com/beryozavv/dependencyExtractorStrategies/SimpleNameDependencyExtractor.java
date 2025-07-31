package com.beryozavv.dependencyExtractorStrategies;

import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.SimpleName;

import java.util.List;

/**
 * Стратегия для извлечения зависимостей из простых имен
 */
public class SimpleNameDependencyExtractor implements DependencyExtractorStrategy<SimpleName> {

    @Override
    public void extractDependencies(SimpleName node, List<String> dependencies) {
        IBinding binding = node.resolveBinding();
        if (binding == null) return;

        if (binding instanceof ITypeBinding) {
            // Ссылка на тип (класс, интерфейс, enum)
            ITypeBinding typeBinding = (ITypeBinding) binding;
            dependencies.add(typeBinding.getQualifiedName());
        } else if (binding instanceof IVariableBinding) {
            // Ссылка на переменную или поле
            IVariableBinding varBinding = (IVariableBinding) binding;
            ITypeBinding typeBinding = varBinding.getType();
            if (typeBinding != null && !typeBinding.isPrimitive()) {
                dependencies.add(typeBinding.getQualifiedName());
            }
        } else if (binding instanceof IMethodBinding) {
            // Ссылка на метод
            IMethodBinding methodBinding = (IMethodBinding) binding;
            ITypeBinding declaringClass = methodBinding.getDeclaringClass();
            if (declaringClass != null) {
                dependencies.add(declaringClass.getQualifiedName());
            }
        }
    }
}
