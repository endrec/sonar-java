/*
 * SonarQube Java
 * Copyright (C) 2012 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.java.checks;

import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.java.checks.methods.MethodInvocationMatcher;
import org.sonar.plugins.java.api.JavaFileScanner;
import org.sonar.plugins.java.api.JavaFileScannerContext;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.tree.AssignmentExpressionTree;
import org.sonar.plugins.java.api.tree.BaseTreeVisitor;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.MemberSelectExpressionTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.ParenthesizedTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.VariableTree;
import org.sonar.squidbridge.annotations.ActivatedByDefault;
import org.sonar.squidbridge.annotations.SqaleConstantRemediation;
import org.sonar.squidbridge.annotations.SqaleSubCharacteristic;

import javax.annotation.Nullable;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

@Rule(
  key = "S2976",
  name = "\"File.createTempFile\" should not be used to create a directory",
  tags = {"owasp-a9", "security"},
  priority = Priority.CRITICAL)
@ActivatedByDefault
@SqaleSubCharacteristic(RulesDefinition.SubCharacteristics.API_ABUSE)
@SqaleConstantRemediation("5min")
public class FileCreateTempFileCheck extends BaseTreeVisitor implements JavaFileScanner {

  private enum State {
    CREATE_TMP_FILE,
    DELETE,
    MKDIR
  }

  private static final String JAVA_IO_FILE = "java.io.File";
  private static final MethodInvocationMatcher FILE_CREATE_TEMP_FILE = MethodInvocationMatcher.create()
    .typeDefinition(JAVA_IO_FILE).name("createTempFile").withNoParameterConstraint();
  private static final MethodInvocationMatcher FILE_DELETE = MethodInvocationMatcher.create()
    .typeDefinition(JAVA_IO_FILE).name("delete");
  private static final MethodInvocationMatcher FILE_MKDIR = MethodInvocationMatcher.create()
    .typeDefinition(JAVA_IO_FILE).name("mkdir");

  private final Deque<Map<Symbol, State>> symbolStack = new LinkedList<>();
  private JavaFileScannerContext context;

  @Override
  public void scanFile(final JavaFileScannerContext context) {
    this.context = context;
    scan(context.getTree());
  }

  @Override
  public void visitMethod(MethodTree tree) {
    symbolStack.push(new HashMap<Symbol, State>());
    super.visitMethod(tree);
    symbolStack.pop();
  }

  @Override
  public void visitAssignmentExpression(AssignmentExpressionTree tree) {
    super.visitAssignmentExpression(tree);
    if (isFileCreateTempFile(tree.expression())) {
      ExpressionTree variable = tree.variable();
      if (variable.is(Tree.Kind.IDENTIFIER) && !symbolStack.isEmpty()) {
        symbolStack.peek().put(((IdentifierTree) variable).symbol(), State.CREATE_TMP_FILE);
      }
    }
  }

  @Override
  public void visitVariable(VariableTree tree) {
    super.visitVariable(tree);
    ExpressionTree initializer = tree.initializer();
    if (initializer != null && isFileCreateTempFile(initializer)) {
      Symbol symbol = tree.symbol();
      if (!symbolStack.isEmpty()) {
        symbolStack.peek().put(symbol, State.CREATE_TMP_FILE);
      }
    }
  }

  private boolean isFileCreateTempFile(ExpressionTree givenExpression) {
    ExpressionTree expressionTree = removeParenthesis(givenExpression);
    return expressionTree.is(Tree.Kind.METHOD_INVOCATION) && FILE_CREATE_TEMP_FILE.matches((MethodInvocationTree) expressionTree);
  }

  @Override
  public void visitMethodInvocation(MethodInvocationTree mit) {
    super.visitMethodInvocation(mit);
    if (FILE_DELETE.matches(mit)) {
      checkAndAdvanceState(mit, State.CREATE_TMP_FILE, State.DELETE);
    } else if (FILE_MKDIR.matches(mit) && State.MKDIR.equals(checkAndAdvanceState(mit, State.DELETE, State.MKDIR))) {
      context.addIssue(mit, this, "Use \"Files.createTempDirectory\" or a library function to create this directory instead.");
    }
  }

  @Nullable
  private State checkAndAdvanceState(MethodInvocationTree mit, State requiredState, State nextState) {
    ExpressionTree methodSelect = mit.methodSelect();
    if (methodSelect.is(Tree.Kind.MEMBER_SELECT)) {
      ExpressionTree expressionTree = ((MemberSelectExpressionTree) methodSelect).expression();
      if (expressionTree.is(Tree.Kind.IDENTIFIER)) {
        Symbol symbol = ((IdentifierTree) expressionTree).symbol();
        Map<Symbol, State> symbolStateMap = symbolStack.peek();
        if (symbolStateMap != null && symbolStateMap.containsKey(symbol) && requiredState.equals(symbolStateMap.get(symbol))) {
          symbolStateMap.put(symbol, nextState);
          return nextState;
        }
      }
    }
    return null;
  }

  private static ExpressionTree removeParenthesis(ExpressionTree tree) {
    ExpressionTree result = tree;
    while (result.is(Tree.Kind.PARENTHESIZED_EXPRESSION)) {
      result = ((ParenthesizedTree) result).expression();
    }
    return result;
  }
}
