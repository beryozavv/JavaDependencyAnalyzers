package com.beryozavv.dependencyExtractorStrategies;

import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.QualifiedName;

import java.util.List;

/**
 * Стратегия для извлечения зависимостей из квалифицированных имен
 */
public class QualifiedNameDependencyExtractor implements DependencyExtractorStrategy<QualifiedName> {

    @Override
    public void extractDependencies(QualifiedName node, List<String> dependencies) {
        IBinding binding = node.resolveBinding();
        if (binding == null) return;

        if (binding instanceof ITypeBinding) {
            // Полное имя типа
            ITypeBinding typeBinding = (ITypeBinding) binding;
            dependencies.add(typeBinding.getQualifiedName());
        } else if (binding instanceof IVariableBinding) {
            // Ссылка на поле
            IVariableBinding varBinding = (IVariableBinding) binding;

            // Тип поля
            ITypeBinding fieldType = varBinding.getType();
            if (fieldType != null && !fieldType.isPrimitive()) {
                dependencies.add(fieldType.getQualifiedName());
            }

            // Тип класса, в котором объявлено поле
            ITypeBinding declaringClass = varBinding.getDeclaringClass();
            if (declaringClass != null) {
                dependencies.add(declaringClass.getQualifiedName());
            }
        }
    }
}
