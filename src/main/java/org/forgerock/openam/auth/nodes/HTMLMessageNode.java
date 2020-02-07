/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2017-2018 ForgeRock AS.
 */


package org.forgerock.openam.auth.nodes;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import javax.inject.Inject;

import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.SingleOutcomeNode;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.util.i18n.PreferredLocales;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.assistedinject.Assisted;
import com.sun.identity.authentication.callbacks.HiddenValueCallback;
import com.sun.identity.authentication.callbacks.ScriptTextOutputCallback;
import com.sun.identity.shared.debug.Debug;
import com.sun.identity.sm.RequiredValueValidator;

@Node.Metadata(outcomeProvider = HTMLMessageNode.OutcomeProvider.class,
        configClass = HTMLMessageNode.Config.class)
public class HTMLMessageNode extends SingleOutcomeNode {

    private static final String BUNDLE = HTMLMessageNode.class.getName().replace(".", "/");
    private final Logger logger = LoggerFactory.getLogger(HTMLMessageNode.class);
    private final static String DEBUG_FILE = "HTMLMessageNode";
	private static final String VALIGN_NEUTRAL_ANCHOR = "HTMLMessageNode_vAlign_Neutral";
    protected Debug debug = Debug.getInstance(DEBUG_FILE);
    private final LocaleSelector localeSelector;
    
    private final Config config;

    /*
     * Constructs a new GetSessionPropertiesNode instance.
     * We can have Assisted:
     * * Config config
     * * UUID nodeId
     *
     * We may want to Inject:
     * CoreWrapper
     */
    @Inject
    public HTMLMessageNode(@Assisted Config config, LocaleSelector localeSelector) {
		this.localeSelector = localeSelector;
		this.config = config;
    }

    /**
     * Configuration for the node.
     * It can have as many attributes as needed, or none.
     */
    public interface Config {
    	
        @Attribute(order = 100)
        default Map<Locale, String> message() { return Collections.emptyMap(); }

        @Attribute(order = 200, validators = {RequiredValueValidator.class})
        default VAlign vAlign() { return VAlign.NEUTRAL; }

        @Attribute(order = 300, validators = {RequiredValueValidator.class})
        default HAlign hAlign() { return HAlign.CENTER; }
        
        @Attribute(order = 400, validators = {RequiredValueValidator.class})
        default boolean box() { return true; }

        @Attribute(order = 500, validators = {RequiredValueValidator.class})
        default boolean overwriteButton() { return false; }

        @Attribute(order = 600)
        default Map<Locale, String> overwriteButtonText() { return Collections.emptyMap(); }
        
    }
    
    public enum VAlign {
        TOP,
        NEUTRAL,
        BOTTOM
    }
    
    public enum HAlign {
        LEFT("left"),
        CENTER("center"),
        RIGHT("right");

        private String hAlign;
        private HAlign(String hAlign) {
            this.hAlign = hAlign;
        }
       
        @Override
        public String toString(){
            return hAlign;
        } 
    }
    
    public enum Style {
        header,
        top,
        neutral,
        bottom,
        footer
    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {
        JsonValue sharedState = context.sharedState;
        JsonValue transientState = context.transientState;

        if (context.hasCallbacks()) {
        	debug.error("[" + DEBUG_FILE + "]: Done.");
            return goToNext().build();
        }
    	debug.error("[" + DEBUG_FILE + "]: Display message.");
        ScriptTextOutputCallback scriptAndSelfSubmitCallback = new ScriptTextOutputCallback(createClientSideScriptExecutorFunction(getScript(context)));
        return Action.send(Arrays.asList(
        		scriptAndSelfSubmitCallback,
        		new HiddenValueCallback(VALIGN_NEUTRAL_ANCHOR)
        		)).build();

    }
    
    private String getScript(TreeContext context) {
    	String message = substitute(context, getLocalisedMessage(context, config.message(), "default.message"));
        // client-side script to change the look and feel of how the terms and conditions are displayed.
    	StringBuffer script = new StringBuffer()
			.append("var callbackScript = document.createElement(\"script\");\n")
			.append("callbackScript.type = \"text/javascript\";\n")
			.append("callbackScript.text = \"function completed() { document.querySelector(\\\"input[type=submit]\\\").click(); }\";\n")
			.append("document.body.appendChild(callbackScript);\n")
			.append("\n")
			.append("submitted = true;\n")
			.append("\n")
			.append("var decodeHTML = function (html) {\n")
			.append("    var txt = document.createElement('textarea');\n")
			.append("    txt.innerHTML = html;\n")
			.append("    return txt.value;\n")
			.append("};")
			.append("\n")
			.append("function insertAfter(el, referenceNode) {\n")
			.append("    referenceNode.parentNode.insertBefore(el, referenceNode.nextSibling);\n")
			.append("}\n")
			.append("\n")
			.append("function callback() {\n")
			.append("\n")
			.append("    var message = document.createElement(\"div\");\n")
			.append("    message.align = \"").append(config.hAlign()).append("\";\n")
			.append("    message.className = \"form-group ").append(config.box() ? "form-control " : "aria-label ").append("\";\n")
			.append("    message.style = \"height: auto;\";\n")
			.append("    message.innerHTML = '").append(message).append("';\n")
			.append("\n");
    	if (config.vAlign() == VAlign.NEUTRAL) {
	    	script
	    		.append("    var anchor = document.getElementById(\"").append(VALIGN_NEUTRAL_ANCHOR).append("\").parentNode;\n")
	    		.append("    insertAfter(message, anchor);");
    	} 
    	else {
	    	script.append("    var anchor = document.forms[0].getElementsByTagName(\"fieldset\")[0];\n");
	    	if (config.vAlign() == VAlign.TOP) {
	        	script.append("    anchor.prepend(message);\n");
	    	}
	    	else {
	        	script.append("    anchor.append(message);\n");
	    	}
			script.append("\n");
    	}
    	if (config.overwriteButton()) {
	    	String overwriteButtonText = substitute(context, getLocalisedMessage(context, config.overwriteButtonText(), "default.overwriteButtonText"));
	    	debug.error("[" + DEBUG_FILE + "]: Overwriting button: " + overwriteButtonText);
	    	script
	    		.append("    var button = document.getElementById(\"loginButton_0\");\n")
	    		.append("    button.value = \"").append(overwriteButtonText).append("\";\n")
	    		.append("\n");
    	}
    	script
			.append("}\n")
			.append("\n")
			.append("if (document.readyState !== 'loading') {\n")
			.append("  callback();\n")
			.append("} else {\n")
			.append("  document.addEventListener(\"DOMContentLoaded\", callback);\n")
			.append("}");
        
        return script.toString();
    }

    public static String createClientSideScriptExecutorFunction(String script) {
        return String.format(
                "(function(output) {\n" +
                "    var autoSubmitDelay = 0,\n" +
                "        submitted = false;\n" +
                "    function submit() {\n" +
                "        if (submitted) {\n" +
                "            return;\n" +
                "        }" +
                "        document.forms[0].submit();\n" +
                "        submitted = true;\n" +
                "    }\n" +
                "    %s\n" + // script
                "    setTimeout(submit, autoSubmitDelay);\n" +
                "}) (document.forms[0].elements['nada']);\n",
                script);
    }
    
    private String substitute(TreeContext context, String input) {
    	java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{{2}([\\w]+?)\\}{2}");
    	java.util.regex.Matcher matcher = pattern.matcher(input);

    	String key, value;
    	HashMap<String, String> vars = new HashMap<String, String>();
    	while(matcher.find()) {
    		key = matcher.group(1);
    		value = context.sharedState.get(key).asString();
    		if (null != value && !vars.containsKey(key)) {
    			logger.debug("Looked-up variable reference: "+key+"="+value);
    			vars.put(key, value);
    		}
    		else {
    			logger.debug("Shared state property not found: "+key);
    			debug.error("[" + DEBUG_FILE + "]: Shared state property not found: "+key);
    		}
    	}
    	
    	String k, v;
    	Iterator<String> iter = vars.keySet().iterator();
    	while ( iter.hasNext() ) {
    		k = iter.next();
    		v = vars.get(k);
    		logger.debug("Replacing: {{"+k+"}} with "+v);
    		input = input.replace("{{"+k+"}}", v);
    	};
    	
    	return input;
    }

    private String getLocalisedMessage(TreeContext context, Map<Locale, String> localisations,
            String defaultMessageKey) {
        PreferredLocales preferredLocales = context.request.locales;
        Locale bestLocale = localeSelector.getBestLocale(preferredLocales, localisations.keySet());

        if (bestLocale != null) {
            return localisations.get(bestLocale);
        } else if (localisations.size() > 0) {
            return localisations.get(localisations.keySet().iterator().next());
        }

        ResourceBundle bundle = preferredLocales.getBundleInPreferredLocale(BUNDLE, HTMLMessageNode.class.getClassLoader());
        return bundle.getString(defaultMessageKey);
    }
    
}
