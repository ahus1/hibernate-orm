/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.ast.produce.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.hibernate.sql.SqlExpressableType;
import org.hibernate.sql.ast.Clause;
import org.hibernate.sql.ast.consume.spi.SqlAstWalker;
import org.hibernate.sql.ast.produce.spi.SqlAstCreationState;
import org.hibernate.sql.ast.produce.spi.SqlAstProcessingState;
import org.hibernate.sql.ast.produce.spi.SqlAstQuerySpecProcessingState;
import org.hibernate.sql.ast.tree.spi.QuerySpec;
import org.hibernate.sql.ast.tree.spi.expression.Expression;
import org.hibernate.sql.results.internal.EmptySqlSelection;
import org.hibernate.sql.results.spi.SqlSelection;
import org.hibernate.type.descriptor.java.spi.BasicJavaDescriptor;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * @author Steve Ebersole
 */
public class SqlAstQuerySpecProcessingStateImpl
		extends SqlAstProcessingStateImpl
		implements SqlAstQuerySpecProcessingState {

	private final QuerySpec querySpec;

	private final Supplier<Consumer<SqlSelection>> sqlSelectionConsumerSupplier;

	public SqlAstQuerySpecProcessingStateImpl(
			QuerySpec querySpec,
			SqlAstProcessingState parent,
			SqlAstCreationState creationState,
			Supplier<Clause> currentClauseAccess,
			Supplier<Consumer<Expression>> resolvedExpressionConsumerAccess,
			Supplier<Consumer<SqlSelection>> sqlSelectionConsumerSupplier) {
		super( parent, creationState, currentClauseAccess, resolvedExpressionConsumerAccess );
		this.querySpec = querySpec;
		this.sqlSelectionConsumerSupplier = sqlSelectionConsumerSupplier;
	}

	@Override
	public QuerySpec getInflightQuerySpec() {
		return querySpec;
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// SqlExpressionResolver

	private Map<Expression, SqlSelection> sqlSelectionMap;
	private int nonEmptySelections = 0;

	@Override
	protected Map<Expression, SqlSelection> sqlSelectionMap() {
		return sqlSelectionMap;
	}

	@Override
	public SqlSelection resolveSqlSelection(
			Expression expression,
			BasicJavaDescriptor javaTypeDescriptor,
			TypeConfiguration typeConfiguration) {
		final SqlSelection existing;
		if ( sqlSelectionMap == null ) {
			sqlSelectionMap = new HashMap<>();
			existing = null;
		}
		else {
			existing = sqlSelectionMap.get( expression );
		}

		if ( existing != null ) {
			return existing;
		}

		final SqlSelection sqlSelection = expression.createSqlSelection(
				nonEmptySelections + 1,
				sqlSelectionMap.size(),
				javaTypeDescriptor,
				typeConfiguration
		);

		sqlSelectionMap.put( expression, sqlSelection );

		if ( !( sqlSelection instanceof EmptySqlSelection ) ) {
			nonEmptySelections++;
		}

		querySpec.getSelectClause().addSqlSelection( sqlSelection );

		sqlSelectionConsumerSupplier.get().accept( sqlSelection );

		return sqlSelection;
	}

	@Override
	public SqlSelection emptySqlSelection() {
		final EmptySqlSelection sqlSelection = new EmptySqlSelection( sqlSelectionMap.size() );
		sqlSelectionMap.put( EmptyExpression.EMPTY_EXPRESSION, sqlSelection );

		sqlSelectionConsumerSupplier.get().accept( sqlSelection );

		return sqlSelection;
	}

	public static class EmptyExpression implements Expression {
		@SuppressWarnings("WeakerAccess")
		public static final EmptyExpression EMPTY_EXPRESSION = new EmptyExpression();

		private EmptyExpression() {
		}

		@Override
		public SqlSelection createSqlSelection(
				int jdbcPosition,
				int valuesArrayPosition,
				BasicJavaDescriptor javaTypeDescriptor,
				TypeConfiguration typeConfiguration) {
			return null;
		}

		@Override
		public SqlExpressableType getType() {
			return null;
		}

		@Override
		public void accept(SqlAstWalker sqlTreeWalker) {

		}
	}
}