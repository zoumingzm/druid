/*
 * Copyright 1999-2017 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.druid.wall.spi;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.ast.expr.*;
import com.alibaba.druid.sql.ast.statement.*;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.*;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleMultiInsertStatement.InsertIntoClause;
import com.alibaba.druid.sql.dialect.oracle.visitor.OracleASTVisitorAdapter;
import com.alibaba.druid.wall.*;
import com.alibaba.druid.wall.violation.ErrorCode;
import com.alibaba.druid.wall.violation.IllegalSQLObjectViolation;

import java.util.ArrayList;
import java.util.List;

public class OracleWallVisitor extends OracleASTVisitorAdapter implements WallVisitor {

    private final WallConfig config;
    private final WallProvider provider;
    private final List<Violation> violations      = new ArrayList<Violation>();
    private boolean               sqlModified     = false;
    private boolean               sqlEndOfComment = false;
    private List<WallUpdateCheckItem> updateCheckItems;

    public OracleWallVisitor(WallProvider provider){
        this.config = provider.getConfig();
        this.provider = provider;
    }

    @Override
    public DbType getDbType() {
        return DbType.oracle;
    }

    @Override
    public boolean isSqlModified() {
        return sqlModified;
    }

    @Override
    public void setSqlModified(boolean sqlModified) {
        this.sqlModified = sqlModified;
    }

    @Override
    public WallProvider getProvider() {
        return provider;
    }

    @Override
    public WallConfig getConfig() {
        return config;
    }

    @Override
    public void addViolation(Violation violation) {
        this.violations.add(violation);
    }

    @Override
    public List<Violation> getViolations() {
        return violations;
    }

    public boolean visit(SQLIdentifierExpr x) {
        String name = x.getName();
        name = WallVisitorUtils.form(name);
        if (config.isVariantCheck() && config.getDenyVariants().contains(name)) {
            getViolations().add(new IllegalSQLObjectViolation(ErrorCode.VARIANT_DENY, "variable not allow : " + name,
                                                              toSQL(x)));
        }
        return true;
    }

    public boolean visit(OracleSelectTableReference x) {
        return WallVisitorUtils.check(this, x);
    }

    @Override
    public boolean isDenyTable(String name) {
        if (!config.isTableCheck()) {
            return false;
        }

        name = WallVisitorUtils.form(name);
        if (name.startsWith("v$") || name.startsWith("v_$")) {
            return true;
        }
        return !this.provider.checkDenyTable(name);
    }

    @Override
    public boolean visit(InsertIntoClause x) {
        WallVisitorUtils.checkInsert(this, x);

        return true;
    }

    @Override
    public boolean visit(OracleMultiInsertStatement x) {
        if (!config.isInsertAllow()) {
            this.getViolations().add(new IllegalSQLObjectViolation(ErrorCode.INSERT_NOT_ALLOW, "insert not allow",
                                                                   this.toSQL(x)));
            return false;
        }
        WallVisitorUtils.initWallTopStatementContext();

        return true;
    }

    @Override
    public void endVisit(OracleMultiInsertStatement x) {
        WallVisitorUtils.clearWallTopStatementContext();
    }

    @Override
    public boolean isSqlEndOfComment() {
        return this.sqlEndOfComment;
    }

    @Override
    public void setSqlEndOfComment(boolean sqlEndOfComment) {
        this.sqlEndOfComment = sqlEndOfComment;
    }

    public void addWallUpdateCheckItem(WallUpdateCheckItem item) {
        if (updateCheckItems == null) {
            updateCheckItems = new ArrayList<WallUpdateCheckItem>();
        }
        updateCheckItems.add(item);
    }

    public List<WallUpdateCheckItem> getUpdateCheckItems() {
        return updateCheckItems;
    }

    public boolean visit(SQLJoinTableSource x) {
        WallVisitorUtils.check(this, x);
        return true;
    }
}
