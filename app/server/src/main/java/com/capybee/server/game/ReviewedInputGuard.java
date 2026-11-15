package com.capybee.server.game;

import com.capybee.engine.input.*;
import com.capybee.engine.port.InputGuard;
import org.springframework.stereotype.Component;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Shared local host guard for wishes and gameplay. This is not clinical triage. */
@Component
public class ReviewedInputGuard implements InputGuard {
    private static final Pattern EMERGENCY=Pattern.compile("chce sie zabic|nie chce zyc|zrobie sobie krzywde|samoboj|want to die|kill myself|hurt myself");
    private static final Pattern DISTRESS=Pattern.compile("jest mi smutno|boje sie|jestem smutn|bardzo tesknie|i am sad|i'm sad|i am scared|i'm scared");
    private static final Pattern PERSONAL=Pattern.compile("\\[redacted:|nazywam sie|moj adres|mieszkam (przy|na|w)|moja szkola|my name is|my address|i live at");
    private static final Pattern INJECTION=Pattern.compile("zignoruj|ignoruj.*instruk|ujawnij.*(prompt|instruk)|haslo|klucz api|ignore (previous|all)|system prompt|developer message|password|api key");
    public InputSafetyDecision inspect(SafeTextInput input,InputPolicy policy) {
        if(input==null||policy==null||!policy.freeTextAllowed())return decision(InputSafetyAction.UNAVAILABLE,SafetyCategory.UNKNOWN,"INPUT_POLICY_UNAVAILABLE");
        String value=Normalizer.normalize(input.text().toLowerCase(Locale.ROOT).replace('ł','l'),Normalizer.Form.NFD).replaceAll("\\p{M}","");
        if(EMERGENCY.matcher(value).find())return decision(InputSafetyAction.BLOCK,SafetyCategory.SELF_HARM_OR_CRISIS,"INPUT_EMERGENCY");
        if(DISTRESS.matcher(value).find())return decision(InputSafetyAction.BLOCK,SafetyCategory.OTHER_UNSAFE_CONTENT,"INPUT_SUPPORT");
        if(PERSONAL.matcher(value).find())return decision(InputSafetyAction.BLOCK,SafetyCategory.PERSONAL_DATA,"INPUT_PERSONAL_DATA");
        if(INJECTION.matcher(value).find())return decision(InputSafetyAction.BLOCK,SafetyCategory.PROMPT_INJECTION_OR_POLICY_BYPASS,"INPUT_POLICY_BLOCKED");
        return decision(InputSafetyAction.ALLOW,SafetyCategory.UNKNOWN,"INPUT_APPROVED");
    }
    private InputSafetyDecision decision(InputSafetyAction action,SafetyCategory category,String code){return new InputSafetyDecision(action,category,code);}
}
