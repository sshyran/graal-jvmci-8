/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.graal.nodes.java;

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.spi.types.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;

/**
 * The {@code InstanceOfNode} represents an instanceof test.
 */
public final class InstanceOfNode extends TypeCheckNode implements Canonicalizable, LIRLowerable, ConditionalTypeFeedbackProvider, TypeCanonicalizable {

    private final boolean negated;

    public boolean negated() {
        return negated;
    }

    /**
     * Constructs a new InstanceOfNode.
     *
     * @param targetClassInstruction the instruction which produces the target class of the instanceof check
     * @param targetClass the class which is the target of the instanceof check
     * @param object the instruction producing the object input to this instruction
     */
    public InstanceOfNode(ValueNode targetClassInstruction, RiResolvedType targetClass, ValueNode object, boolean negated) {
        this(targetClassInstruction, targetClass, object, null, negated);
    }

    public InstanceOfNode(ValueNode targetClassInstruction, RiResolvedType targetClass, ValueNode object, RiTypeProfile profile, boolean negated) {
        super(targetClassInstruction, targetClass, object, profile, StampFactory.illegal());
        this.negated = negated;
        assert targetClass != null;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        assert object() != null : this;

        RiResolvedType exact = object().exactType();
        if (exact != null) {
            boolean subType = exact.isSubtypeOf(targetClass());

            if (subType) {
                if (object().stamp().nonNull()) {
                    // the instanceOf matches, so return true (or false, for the negated case)
                    return ConstantNode.forBoolean(!negated, graph());
                } else {
                    // the instanceof matches if the object is non-null, so return true (or false, for the negated case) depending on the null-ness.
                    return graph().unique(new NullCheckNode(object(), negated));
                }
            } else {
                // since this type check failed for an exact type we know that it can never succeed at run time.
                // we also don't care about null values, since they will also make the check fail.
                // so return false (or true, for the negated case)
                return ConstantNode.forBoolean(negated, graph());
            }
        } else {
            RiResolvedType declared = object().declaredType();
            if (declared != null) {
                boolean subType = declared.isSubtypeOf(targetClass());

                if (subType) {
                    if (object().stamp().nonNull()) {
                        // the instanceOf matches, so return true (or false, for the negated case)
                        return ConstantNode.forBoolean(!negated, graph());
                    } else {
                        // the instanceof matches if the object is non-null, so return true (or false, for the negated case) depending on the null-ness.
                        return graph().unique(new NullCheckNode(object(), negated));
                    }
                } else {
                    // since the subtype comparison was only performed on a declared type we don't really know if it might be true at run time...
                }
            }
        }

        CiConstant constant = object().asConstant();
        if (constant != null) {
            assert constant.kind == CiKind.Object;
            if (constant.isNull()) {
                return ConstantNode.forBoolean(negated, graph());
            } else {
                assert false : "non-null constants are always expected to provide an exactType";
            }
        }
//        if (tool.assumptions() != null && hints() != null && targetClass() != null) {
//            if (!hintsExact() && hints().length == 1 && hints()[0] == targetClass().uniqueConcreteSubtype()) {
//                tool.assumptions().recordConcreteSubtype(targetClass(), hints()[0]);
//                return graph().unique(new InstanceOfNode(targetClassInstruction(), targetClass(), object(), hints(), true, negated));
//            }
//        }
        return this;
    }

    @Override
    public BooleanNode negate() {
        return graph().unique(new InstanceOfNode(targetClassInstruction(), targetClass(), object(), profile(), !negated));
    }

    @Override
    public void typeFeedback(TypeFeedbackTool tool) {
        if (negated) {
            tool.addObject(object()).notDeclaredType(targetClass(), true);
        } else {
            tool.addObject(object()).declaredType(targetClass(), true);
        }
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name && negated) {
            return "!" + super.toString(Verbosity.Name);
        } else {
            return super.toString(verbosity);
        }
    }

    @Override
    public Result canonical(TypeFeedbackTool tool) {
        ObjectTypeQuery query = tool.queryObject(object());
        if (query.constantBound(Condition.EQ, CiConstant.NULL_OBJECT)) {
            return new Result(ConstantNode.forBoolean(negated, graph()), query);
        } else if (targetClass() != null) {
            if (query.notDeclaredType(targetClass())) {
                return new Result(ConstantNode.forBoolean(negated, graph()), query);
            }
            if (query.constantBound(Condition.NE, CiConstant.NULL_OBJECT)) {
                if (query.declaredType(targetClass())) {
                    return new Result(ConstantNode.forBoolean(!negated, graph()), query);
                }
            }
        }
        return null;
    }
}
