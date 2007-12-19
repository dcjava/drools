package org.drools.ruleflow.instance.impl.configuration;

import org.drools.ruleflow.core.Node;
import org.drools.ruleflow.core.RuleSetNode;
import org.drools.ruleflow.instance.RuleFlowNodeInstance;
import org.drools.ruleflow.instance.impl.PvmNodeConf;
import org.drools.ruleflow.instance.impl.RuleFlowProcessInstanceImpl;
import org.drools.ruleflow.instance.impl.RuleFlowSplitInstanceImpl;

public class SplitNodeConf implements PvmNodeConf {

	public RuleFlowNodeInstance getNodeInstance(Node node, RuleFlowProcessInstanceImpl processInstance ) {    	
        RuleFlowNodeInstance result = processInstance.getFirstNodeInstance( node.getId() );
        if ( result == null ) {
            result = new RuleFlowSplitInstanceImpl();
            result.setNodeId( node.getId() );
            processInstance.addNodeInstance( result );
        }
        return result;    	
	}

}