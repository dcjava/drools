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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import org.drools.FactHandle;
import org.drools.RuntimeDroolsException;
import org.drools.base.ModifyInterceptor;
import org.drools.common.AgendaItem;
import org.drools.common.InternalFactHandle;
import org.drools.common.InternalWorkingMemory;
import org.drools.definition.rule.Rule;
import org.drools.reteoo.LeftTuple;
import org.drools.rule.Declaration;
import org.drools.spi.KnowledgeHelper;
import org.mvel2.DataConversion;
import org.mvel2.MVEL;
import org.mvel2.ParserConfiguration;
import org.mvel2.ParserContext;
import org.mvel2.UnresolveablePropertyException;
import org.mvel2.integration.VariableResolver;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.BaseVariableResolverFactory;
import org.mvel2.integration.impl.CachingMapVariableResolverFactory;
import org.mvel2.integration.impl.IndexVariableResolver;
import org.mvel2.integration.impl.IndexedVariableResolverFactory;
import org.mvel2.integration.impl.MapVariableResolverFactory;

public class MVELCompilationUnit
    implements
    Externalizable,
    Cloneable {

    private static final long serialVersionUID = 510l;

    private String                          name;
    
    private String                          expression;

    private String[]                        pkgImports;
    private String[]                        importClasses;
    private String[]                        importMethods;
    private String[]                        importFields;
    private String[]                        globalIdentifiers;

    private Declaration[]                   previousDeclarations;
    private Declaration[]                   localDeclarations;
    private String[]                        otherIdentifiers;

    private String[]                        inputIdentifiers;
    private String[]                        inputTypes;

    private String[]                        shadowIdentifiers;

    private int                             languageLevel;
    private boolean                         strictMode;

    private static Map                      interceptors  = new HashMap( 2 );
    static {
        interceptors.put( "Modify",
                          new ModifyInterceptor() );
    }
    
    static {
        //for handling dates as string literals
        DataConversion.addConversionHandler( Date.class,
                                             new MVELDateCoercion() );
        DataConversion.addConversionHandler( Calendar.class,
                                             new MVELCalendarCoercion() );
    }

    private static final Map<String, Class> primitivesMap = new HashMap<String, Class>();
    static {
        primitivesMap.put( "int",
                           int.class );
        primitivesMap.put( "boolean",
                           boolean.class );
        primitivesMap.put( "float",
                           float.class );
        primitivesMap.put( "long",
                           long.class );
        primitivesMap.put( "short",
                           short.class );
        primitivesMap.put( "byte",
                           byte.class );
        primitivesMap.put( "double",
                           double.class );
        primitivesMap.put( "char",
                           char.class );
    }

    public static final Object              COMPILER_LOCK = new Object();

    public MVELCompilationUnit() {
    }

    public MVELCompilationUnit(String name, 
                               String expression,
                               String[] pkgImports,
                               String[] importClasses,
                               String[] importMethods,
                               String[] importFields,
                               String[] globalIdentifiers,
                               Declaration[] previousDeclarations,
                               Declaration[] localDeclarations,
                               String[] otherIdentifiers,
                               String[] inputIdentifiers,
                               String[] inputTypes,
                               int languageLevel,
                               boolean strictMode) {
        this.name = name;
        this.expression = expression;

        this.pkgImports = pkgImports;
        this.importClasses = importClasses;
        this.importMethods = importMethods;
        this.importFields = importFields;
        this.globalIdentifiers = globalIdentifiers;

        this.previousDeclarations = previousDeclarations;
        this.localDeclarations = localDeclarations;
        this.otherIdentifiers = otherIdentifiers;

        this.inputIdentifiers = inputIdentifiers;
        this.inputTypes = inputTypes;

        this.languageLevel = languageLevel;
        this.strictMode = strictMode;
    }

    public String getExpression() {
        return expression;
    }

    public void setShadowIdentifiers(String[] shadowIdentifiers) {
        this.shadowIdentifiers = shadowIdentifiers;
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF( name );
        
        out.writeUTF( expression );

        out.writeObject( pkgImports );
        out.writeObject( importClasses );
        out.writeObject( importMethods );
        out.writeObject( importFields );
        out.writeObject( globalIdentifiers );

        out.writeObject( previousDeclarations );
        out.writeObject( localDeclarations );
        out.writeObject( otherIdentifiers );

        out.writeObject( inputIdentifiers );
        out.writeObject( inputTypes );

        out.writeObject( shadowIdentifiers );

        out.writeInt( languageLevel );
        out.writeBoolean( strictMode );
    }

    public void readExternal(ObjectInput in) throws IOException,
                                            ClassNotFoundException {
        name = in.readUTF();
        expression = in.readUTF();

        pkgImports = (String[]) in.readObject();
        importClasses = (String[]) in.readObject();
        importMethods = (String[]) in.readObject();
        importFields = (String[]) in.readObject();
        globalIdentifiers = (String[]) in.readObject();

        previousDeclarations = (Declaration[]) in.readObject();
        localDeclarations = (Declaration[]) in.readObject();
        otherIdentifiers = (String[]) in.readObject();

        inputIdentifiers = (String[]) in.readObject();
        inputTypes = (String[]) in.readObject();

        shadowIdentifiers = (String[]) in.readObject();

        languageLevel = in.readInt();
        strictMode = in.readBoolean();
    }

    public Serializable getCompiledExpression(ClassLoader classLoader) {
        Map<String, Object> resolvedImports = new HashMap<String, Object>( importClasses.length + importMethods.length + importFields.length );
        String lastName = null;
        try {
            for ( String name : importClasses ) {
                lastName = name;
                Class cls = loadClass( classLoader,
                                       name );
                resolvedImports.put( cls.getSimpleName(),
                                     cls );
            }

            for ( String name : importMethods ) {
                lastName = name;
                int lastDot = name.lastIndexOf( '.' );
                String className = name.substring( 0,
                                                   lastDot );
                Class cls = loadClass( classLoader,
                                       className );

                String methodName = name.substring( lastDot + 1 );
                Method method = null;
                for ( Method item : cls.getMethods() ) {
                    if ( methodName.equals( item.getName() ) ) {
                        method = item;
                        break;
                    }
                }
                resolvedImports.put( method.getName(),
                                     method );
            }

            for ( String name : importFields ) {
                int lastDot = name.lastIndexOf( '.' );
                String className = name.substring( 0,
                                                   lastDot );
                Class cls = loadClass( classLoader,
                                       className );

                String fieldName = name.substring( lastDot + 1 );
                Field field = cls.getField( fieldName );

                resolvedImports.put( field.getName(),
                                     field );
            }
        } catch ( Exception e ) {
            throw new RuntimeDroolsException( "Unable to resolve import '" + lastName + "'" );
            
        }

        ParserConfiguration conf = new ParserConfiguration();
        conf.setImports( resolvedImports );
        conf.setPackageImports( new HashSet( Arrays.asList( this.pkgImports ) ) );
        conf.setClassLoader( classLoader );
        
        final ParserContext parserContext = new ParserContext( conf );
        if ( MVELDebugHandler.isDebugMode() ) {
            parserContext.setDebugSymbols( true );
        }

        parserContext.setStrictTypeEnforcement( strictMode );
        parserContext.setStrongTyping( strictMode );
        parserContext.setIndexAllocation( true );

        if ( interceptors != null ) {
            parserContext.setInterceptors( interceptors );
        }

        
        parserContext.addIndexedVariables( inputIdentifiers );
        String identifier = null;
        String type = null;
        try {
            for ( int i = 0, length = inputIdentifiers.length; i < length; i++ ) {
                identifier = inputIdentifiers[i];
                type = inputTypes[i];
                Class cls = loadClass( classLoader,
                                       inputTypes[i] );
                parserContext.addInput( inputIdentifiers[i],
                                        cls );
            }
        } catch ( ClassNotFoundException e ) {
            throw new RuntimeDroolsException( "Unable to resolve class '" + type + "' for identifier '" + identifier );
        }
        
        parserContext.setSourceFile( name );
        
        return compile( expression,
                        classLoader,
                        parserContext,
                        languageLevel );
    }
    
    public DroolsMVELIndexedFactory getFactory(final KnowledgeHelper knowledgeHelper,
                                              final Rule rule,
                                              final Object rightObject,
                                              final LeftTuple tuples,
                                              final Object[] otherVars,
                                              final InternalWorkingMemory workingMemory) {
        int varLength = inputIdentifiers.length;
        
        Object[] vals = new Object[varLength];
        
        int i = 0;
        if ( rightObject != null ) {
            vals[i++] = rightObject;
        }
        vals[i++] = knowledgeHelper;
        vals[i++] = knowledgeHelper;
        vals[i++] = rule;
        
        if ( globalIdentifiers != null ) {
            for ( int j = 0, length = globalIdentifiers.length; j < length; j++ ) {
              vals[i++] = workingMemory.getGlobal( this.globalIdentifiers[j] );
            }
        }
        
        IdentityHashMap<Object, FactHandle> identityMap = null;
        if ( knowledgeHelper != null ) {
            identityMap = new IdentityHashMap<Object, FactHandle>();
        }
        
             
        if ( tuples != null ) {
            if ( this.previousDeclarations != null && this.previousDeclarations.length > 0 ) {
                
                // Consequences with 'or's will have different declaration offsets, so use the one's from the RTN's subrule.
                Declaration[] prevDecl = this.previousDeclarations;
                if ( knowledgeHelper != null ) {
                    prevDecl = ((AgendaItem)knowledgeHelper.getActivation()).getRuleTerminalNode().getDeclarations();
                }
                InternalFactHandle[] handles = ((LeftTuple) tuples).toFactHandles();
                
                for ( int j = 0, length = prevDecl.length; j < length; j++ ) {
                    Declaration decl = prevDecl[j];
                    InternalFactHandle handle = getFactHandle( decl, 
                                                               handles );
                    
                    Object o = decl.getValue( (InternalWorkingMemory) workingMemory, handle.getObject() );
                    if ( knowledgeHelper != null && decl.isPatternDeclaration() ) {
                        identityMap.put( decl.getIdentifier(), handle );
                    }
                    vals[i++] = o;
                }
            }
        }
        
        if ( this.localDeclarations != null && this.localDeclarations.length > 0 ) {
            for ( int j = 0, length = this.localDeclarations.length; j < length; j++ ) {
                Declaration decl = this.localDeclarations[j];
                Object o = decl.getValue( (InternalWorkingMemory) workingMemory, rightObject);
                vals[i++] = o;
            }
        }
        
        int otherVarsPos = 0;
        if ( otherVars != null ) {
            otherVarsPos = i;
            for ( Object o : otherVars ) {
                vals[i++] = o;
            }
        }
        int otherVarsLength = i - otherVarsPos;
        
        if ( knowledgeHelper != null ) {
            knowledgeHelper.setIdentityMap( identityMap );
        }
        
        VariableResolverFactory locals = new CachingMapVariableResolverFactory(new HashMap<String, Object>());
        DroolsMVELIndexedFactory factory =  new DroolsMVELIndexedFactory(inputIdentifiers, vals, locals);
        factory.setOtherVarsPos( otherVarsPos );
        factory.setOtherVarsLength( otherVarsLength );
        factory.setKnowledgeHelper( knowledgeHelper );
        return factory;
    }
    
    public static class DroolsMVELIndexedFactory extends BaseVariableResolverFactory {
        
        private KnowledgeHelper knowledgeHelper;
        
        private int otherVarsPos;
        private int otherVarsLength;
        
        private Object[] values;

        public DroolsMVELIndexedFactory(String[] varNames,
                                         Object[] values) {
            this.indexedVariableNames = varNames;
            this.values = values;
            this.indexedVariableResolvers = createResolvers(values);
        }
        
        public DroolsMVELIndexedFactory(String[] varNames,
                                        Object[] values,
                                        VariableResolverFactory factory) {
            this.indexedVariableNames = varNames;
            this.values = values;
            this.nextFactory = new MapVariableResolverFactory();
            this.nextFactory.setNextFactory(factory);
            this.indexedVariableResolvers = createResolvers(values);
        }
        
        private static VariableResolver[] createResolvers(Object[] values) {
            VariableResolver[] vr = new VariableResolver[values.length];
            for (int i = 0; i < values.length; i++) {
                vr[i] = new IndexVariableResolver(i, values);
            }
            return vr;
        }        

        public KnowledgeHelper getKnowledgeHelper() {
            return knowledgeHelper;
        }

        public void setKnowledgeHelper(KnowledgeHelper knowledgeHelper) {
            this.knowledgeHelper = knowledgeHelper;
        }

        public int getOtherVarsPos() {
            return otherVarsPos;
        }

        public void setOtherVarsPos(int otherVarsPos) {
            this.otherVarsPos = otherVarsPos;
        }

        public int getOtherVarsLength() {
            return otherVarsLength;
        }

        public void setOtherVarsLength(int otherVarsLength) {
            this.otherVarsLength = otherVarsLength;
        }
        
        public VariableResolver createIndexedVariable(int index, String name, Object value) {
            indexedVariableResolvers[index].setValue( value );
            return indexedVariableResolvers[index];
        }

        public VariableResolver getIndexedVariableResolver(int index) {
            return indexedVariableResolvers[index];
        }

        public VariableResolver createVariable(String name, Object value) {
            VariableResolver vr = getResolver( name );
            if ( vr != null ) {
                vr.setValue( value );
                return vr;
            } else {
                if (nextFactory == null) nextFactory = new MapVariableResolverFactory(new HashMap());
                return nextFactory.createVariable(name, value);
            }
        }

        public VariableResolver createVariable(String name, Object value, Class<?> type) {
            VariableResolver vr = getResolver( name );
            if ( vr != null ) {
                if ( vr.getType() != null ) {
                    throw new RuntimeException("variable already defined within scope: " + vr.getType() + " " + name);
                } else {
                    vr.setValue( value );
                    return vr;
                }
            } else {
                if (nextFactory == null) nextFactory = new MapVariableResolverFactory(new HashMap());
                return nextFactory.createVariable(name, value, type);
            }
        }

        public VariableResolver getVariableResolver(String name) {
            VariableResolver vr = getResolver(name);
            if (vr != null) return vr;
            else if (nextFactory != null) {
                return nextFactory.getVariableResolver(name);
            }

            throw new UnresolveablePropertyException("unable to resolve variable '" + name + "'");
        }

        public boolean isResolveable(String name) {
            return isTarget(name) || (nextFactory != null && nextFactory.isResolveable(name));
        }

        protected VariableResolver addResolver(String name, VariableResolver vr) {
            variableResolvers.put(name, vr);
            return vr;
        }

        private VariableResolver getResolver(String name) {
            for (int i = 0; i < indexedVariableNames.length; i++) {
                if (indexedVariableNames[i].equals(name)) {
                    return indexedVariableResolvers[i];
                }
            }
            return null;
        }

        public boolean isTarget(String name) {
            for (String indexedVariableName : indexedVariableNames) {
                if (indexedVariableName.equals(name)) {
                    return true;
                }
            }
            return false;
        }

        public Set<String> getKnownVariables() {
            Set<String> vars = new HashSet<String>();
            for (int i = 0; i < indexedVariableNames.length; i++) {
                vars.add( indexedVariableNames[i]  );
            }            
            return vars;
        }

        public void clear() {
            // variableResolvers.clear();

        }

        @Override
        public boolean isIndexedFactory() {
            return true;
        }        
    }
    
    private static InternalFactHandle getFactHandle(Declaration declaration, InternalFactHandle[] handles) {
        return handles[ declaration.getPattern().getOffset() ];
    }

    public static Serializable compile(final String text,
                                       final ClassLoader classLoader,
                                       final ParserContext parserContext,
                                       final int languageLevel) {
        MVEL.COMPILER_OPT_ALLOW_NAKED_METH_CALL = true;

        if ( MVELDebugHandler.isDebugMode() ) {
            parserContext.setDebugSymbols( true );
        }

        Serializable expr = null;
        expr = MVEL.compileExpression( text.trim(), parserContext );

        return expr;
    }

    private Class loadClass(ClassLoader classLoader,
                            String className) throws ClassNotFoundException {
        Class cls = primitivesMap.get( className );
        if ( cls == null ) {
            cls = classLoader.loadClass( className );
        }
        return cls;

    }

    public void replaceDeclaration(Declaration declaration,
                                   Declaration resolved) {
        if( previousDeclarations != null ) {
            for( int i = 0; i < previousDeclarations.length; i++ ) {
                if( previousDeclarations[i].equals( declaration ) ) {
                    previousDeclarations[i] = resolved;
                }
            }
        }
        if( localDeclarations != null ) {
            for( int i = 0; i < localDeclarations.length; i++ ) {
                if( localDeclarations[i].equals( declaration ) ) {
                    localDeclarations[i] = resolved;
                }
            }
        }
    }

    @Override
    public MVELCompilationUnit clone() {
        return new MVELCompilationUnit(name, 
                                       expression,
                                       pkgImports,
                                       importClasses,
                                       importMethods,
                                       importFields,
                                       globalIdentifiers,
                                       previousDeclarations,
                                       localDeclarations,
                                       otherIdentifiers,
                                       inputIdentifiers,
                                       inputTypes,
                                       languageLevel,
                                       strictMode);
    }

    public static long getSerialversionuid() {
        return serialVersionUID;
    }

    public String getName() {
        return name;
    }

    public String[] getPkgImports() {
        return pkgImports;
    }

    public String[] getImportClasses() {
        return importClasses;
    }

    public String[] getImportMethods() {
        return importMethods;
    }

    public String[] getImportFields() {
        return importFields;
    }

    public String[] getGlobalIdentifiers() {
        return globalIdentifiers;
    }

    public Declaration[] getPreviousDeclarations() {
        return previousDeclarations;
    }
    
    public void setPreviousDeclarations(Declaration[] previousDeclarations) {
        this.previousDeclarations = previousDeclarations;
    }    

    public Declaration[] getLocalDeclarations() {
        return localDeclarations;
    }

    public String[] getOtherIdentifiers() {
        return otherIdentifiers;
    }

    public String[] getInputIdentifiers() {
        return inputIdentifiers;
    }

    public String[] getInputTypes() {
        return inputTypes;
    }

    public String[] getShadowIdentifiers() {
        return shadowIdentifiers;
    }

    public int getLanguageLevel() {
        return languageLevel;
    }

    public boolean isStrictMode() {
        return strictMode;
    }

    public static Map getInterceptors() {
        return interceptors;
    }

    public static Map<String, Class> getPrimitivesmap() {
        return primitivesMap;
    }

    public static Object getCompilerLock() {
        return COMPILER_LOCK;
    }
    
    

}
