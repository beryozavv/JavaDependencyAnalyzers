package com.beryozavv.dependencyExtractorStrategies;

import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;

import java.util.List;

/**
 * Стратегия для извлечения зависимостей из вызовов методов
 */
public class MethodInvocationDependencyExtractor implements DependencyExtractorStrategy<MethodInvocation> {

    @Override
    public void extractDependencies(MethodInvocation node, List<String> dependencies) {
        IMethodBinding methodBinding = node.resolveMethodBinding();
        if (methodBinding != null) {
            ITypeBinding declaringClass = methodBinding.getDeclaringClass();
            if (declaringClass != null) {
                dependencies.add(declaringClass.getQualifiedName());
            }

            // Добавляем типы параметров
            for (ITypeBinding paramType : methodBinding.getParameterTypes()) {
                dependencies.add(paramType.getQualifiedName());
            }

            // Добавляем тип возвращаемого значения
            ITypeBinding returnType = methodBinding.getReturnType();
            if (returnType != null && !returnType.isPrimitive()) {
                dependencies.add(returnType.getQualifiedName());
            }
        }
    }
}
