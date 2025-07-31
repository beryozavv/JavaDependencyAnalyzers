package com.beryozavv.dependencyExtractorStrategies;

import org.eclipse.jdt.core.dom.ASTNode;

import java.util.List;

/**
 * Интерфейс стратегии для извлечения зависимостей из различных типов узлов AST
 *
 * @param <T> тип узла AST
 */
public interface DependencyExtractorStrategy<T extends ASTNode> {

    /**
     * Извлекает зависимости из узла AST и добавляет их в предоставленный список
     *
     * @param node узел AST для анализа
     * @param dependencies список для сохранения найденных зависимостей
     */
    void extractDependencies(T node, List<String> dependencies);
}
