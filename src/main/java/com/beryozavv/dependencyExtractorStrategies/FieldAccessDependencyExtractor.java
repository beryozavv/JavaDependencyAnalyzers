package com.beryozavv.dependencyExtractorStrategies;

import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

import java.util.List;

/**
 * Стратегия для извлечения зависимостей из выражений доступа к полям
 */
public class FieldAccessDependencyExtractor implements DependencyExtractorStrategy<FieldAccess> {

    @Override
    public void extractDependencies(FieldAccess node, List<String> dependencies) {
        IVariableBinding fieldBinding = node.resolveFieldBinding();
        if (fieldBinding != null) {
            // Тип поля
            ITypeBinding fieldType = fieldBinding.getType();
            if (fieldType != null && !fieldType.isPrimitive()) {
                dependencies.add(fieldType.getQualifiedName());
            }

            // Тип класса, в котором объявлено поле
            ITypeBinding declaringClass = fieldBinding.getDeclaringClass();
            if (declaringClass != null) {
                dependencies.add(declaringClass.getQualifiedName());
            }
        }
    }
}
