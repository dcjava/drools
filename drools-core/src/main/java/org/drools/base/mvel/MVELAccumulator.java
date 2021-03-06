/*
 * Copyright 2010 JBoss Inc
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

package org.drools.base.mvel;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.drools.base.mvel.MVELCompilationUnit.DroolsMVELIndexedFactory;
import org.drools.common.InternalFactHandle;
import org.drools.common.InternalWorkingMemory;
import org.drools.reteoo.LeftTuple;
import org.drools.rule.Declaration;
import org.drools.rule.MVELDialectRuntimeData;
import org.drools.rule.Package;
import org.drools.WorkingMemory;
import org.drools.spi.Accumulator;
import org.drools.spi.Tuple;
import org.mvel2.MVEL;
import org.mvel2.compiler.CompiledExpression;
import org.mvel2.integration.VariableResolverFactory;

/**
 * An MVEL accumulator implementation
 */
public class MVELAccumulator
    implements
    MVELCompileable,
    Accumulator,
    Externalizable {

    private static final long serialVersionUID = 510l;

    MVELCompilationUnit       initUnit;
    MVELCompilationUnit       actionUnit;
    MVELCompilationUnit       reverseUnit;
    MVELCompilationUnit       resultUnit;
    
    private Serializable      init;
    private Serializable      action;
    private Serializable      reverse;
    private Serializable      result;

    public MVELAccumulator() {
    }

    public MVELAccumulator(final MVELCompilationUnit initUnit,
                           final MVELCompilationUnit actionUnit,
                           final MVELCompilationUnit reverseUnit,
                           final MVELCompilationUnit resultUnit) {
        super();
        this.initUnit = initUnit;
        this.actionUnit = actionUnit;
        this.reverseUnit = reverseUnit;
        this.resultUnit = resultUnit;
    }

    public void readExternal(ObjectInput in) throws IOException,
                                            ClassNotFoundException {
        initUnit = (MVELCompilationUnit) in.readObject();
        actionUnit = (MVELCompilationUnit) in.readObject();
        reverseUnit = (MVELCompilationUnit) in.readObject();
        resultUnit = (MVELCompilationUnit) in.readObject();
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject( initUnit );
        out.writeObject( actionUnit );
        out.writeObject( reverseUnit );
        out.writeObject( resultUnit );
    }

    public void compile(ClassLoader classLoader) {
        init = initUnit.getCompiledExpression( classLoader );
        action = actionUnit.getCompiledExpression( classLoader );
        result = resultUnit.getCompiledExpression( classLoader );
                
        if ( reverseUnit != null ) {
            reverse = reverseUnit.getCompiledExpression( classLoader );
        }
    }

    /* (non-Javadoc)
     * @see org.drools.spi.Accumulator#createContext()
     */
    public Serializable createContext() {
        Map<Integer, DroolsMVELIndexedFactory> shadow = null;
        if ( this.reverse != null ) {
            shadow = new HashMap<Integer, DroolsMVELIndexedFactory>();
        }
        return new MVELAccumulatorContext( shadow );
    }

    /* (non-Javadoc)
     * @see org.drools.spi.Accumulator#init(java.lang.Object, org.drools.spi.Tuple, org.drools.rule.Declaration[], org.drools.WorkingMemory)
     */
    public void init(Object workingMemoryContext,
                     Object context,
                     Tuple leftTuple,
                     Declaration[] declarations,
                     WorkingMemory workingMemory) throws Exception {
        Object[] localVars = new Object[initUnit.getOtherIdentifiers().length];
        DroolsMVELIndexedFactory factory = initUnit.getFactory( null, null, null, (LeftTuple) leftTuple, localVars, (InternalWorkingMemory) workingMemory );
        
        Package pkg = workingMemory.getRuleBase().getPackage( "MAIN" );
        if ( pkg != null ) {
            MVELDialectRuntimeData data = (MVELDialectRuntimeData) pkg.getDialectRuntimeRegistry().getDialectData( "mvel" );
            factory.setNextFactory( data.getFunctionFactory() );
        }

        MVEL.executeExpression( this.init,
                                null,
                                factory );
        
        if ( localVars.length > 0 ) {
            for ( int i = 0; i < factory.getOtherVarsLength(); i++ ) {
                localVars[i] = factory.getIndexedVariableResolver( factory.getOtherVarsPos() + i ).getValue();
            }
        }
        
        ((MVELAccumulatorContext) context).setVariables( localVars );
    }

    /* (non-Javadoc)
     * @see org.drools.spi.Accumulator#accumulate(java.lang.Object, org.drools.spi.Tuple, org.drools.common.InternalFactHandle, org.drools.rule.Declaration[], org.drools.rule.Declaration[], org.drools.WorkingMemory)
     */
    public void accumulate(Object workingMemoryContext,
                           Object context,
                           Tuple leftTuple,
                           InternalFactHandle handle,
                           Declaration[] declarations,
                           Declaration[] innerDeclarations,
                           WorkingMemory workingMemory) throws Exception {
        Object[]  localVars = ((MVELAccumulatorContext) context).getVariables();
        DroolsMVELIndexedFactory factory = actionUnit.getFactory( null, null, handle.getObject(), (LeftTuple) leftTuple, localVars, (InternalWorkingMemory) workingMemory );       

        if ( reverse != null ) {
            // SNAPSHOT variable values
            ((MVELAccumulatorContext) context).getShadow().put( handle.getId(), factory);
        }
        MVEL.executeExpression( this.action,
                                null,
                                factory );
        
        if ( localVars.length > 0 ) {
            for ( int i = 0; i < factory.getOtherVarsLength(); i++ ) {
                localVars[i] = factory.getIndexedVariableResolver( factory.getOtherVarsPos() + i ).getValue();
            }
        }
        
        ((MVELAccumulatorContext) context).setVariables( localVars );        
    }

    public void reverse(Object workingMemoryContext,
                        Object context,
                        Tuple leftTuple,
                        InternalFactHandle handle,
                        Declaration[] declarations,
                        Declaration[] innerDeclarations,
                        WorkingMemory workingMemory) throws Exception {
        Object[]  localVars = ((MVELAccumulatorContext) context).getVariables();
        DroolsMVELIndexedFactory factory = ((MVELAccumulatorContext) context).shadow.remove( handle.getId() );
        // we need the latest local vars
        if ( localVars.length > 0 ) {
            for ( int i = 0; i < factory.getOtherVarsLength(); i++ ) {
                factory.getIndexedVariableResolver( factory.getOtherVarsPos() + i ).setValue(localVars[i]);
            }
        }         

        MVEL.executeExpression( this.reverse,
                                null,
                                factory );
        
        if ( localVars.length > 0 ) {
            for ( int i = 0; i < factory.getOtherVarsLength(); i++ ) {
                localVars[i] = factory.getIndexedVariableResolver( factory.getOtherVarsPos() + i ).getValue();
            }
        }
        
        ((MVELAccumulatorContext) context).setVariables( localVars );         
    }

    /* (non-Javadoc)
     * @see org.drools.spi.Accumulator#getResult(java.lang.Object, org.drools.spi.Tuple, org.drools.rule.Declaration[], org.drools.WorkingMemory)
     */
    public Object getResult(Object workingMemoryContext,
                            Object context,
                            Tuple leftTuple,
                            Declaration[] declarations,
                            WorkingMemory workingMemory) throws Exception {
        Object[]  localVars = ((MVELAccumulatorContext) context).getVariables();
        VariableResolverFactory factory = resultUnit.getFactory( null, null, null, (LeftTuple) leftTuple, localVars, (InternalWorkingMemory) workingMemory );

        final Object result = MVEL.executeExpression( this.result,
                                                      null,
                                                      factory );
        return result;
    }

    public boolean supportsReverse() {
        return this.reverse != null;
    }

    public Object createWorkingMemoryContext() {
        return null;
    }

    private static class MVELAccumulatorContext
        implements
        Serializable {

        private static final long                      serialVersionUID = 510l;

        private Object[]               variables;
        private Map<Integer,DroolsMVELIndexedFactory> shadow;

        public MVELAccumulatorContext(Map<Integer,  DroolsMVELIndexedFactory> shadow) {
            this.shadow = shadow;
        }

        public Object[] getVariables() {
            return variables;
        }

        public void setVariables(Object[] variables) {
            this.variables = variables;
        }

        public Map<Integer, DroolsMVELIndexedFactory> getShadow() {
            return shadow;
        }

        public void setShadow(Map<Integer,  DroolsMVELIndexedFactory> shadow) {
            this.shadow = shadow;
        }
        
        
    }

}
