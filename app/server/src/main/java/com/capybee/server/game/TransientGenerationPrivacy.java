package com.capybee.server.game;

import com.capybee.engine.port.TextGenerator;
import com.capybee.engine.port.TextGenerationResult;
import com.capybee.engine.session.wire.SessionJson;
import java.util.concurrent.Callable;

/** Operation-scoped memory only. Reject literal wish echoes before artifact acceptance. */
public final class TransientGenerationPrivacy {
    private static final ThreadLocal<String> WISH=new ThreadLocal<>();
    private static final ThreadLocal<java.util.function.BooleanSupplier> CANCELLED=new ThreadLocal<>();
    private TransientGenerationPrivacy(){}
    public static <T> T within(String wish,Callable<T> work) throws Exception {
        return within(wish,()->false,work);
    }
    public static <T> T within(String wish,java.util.function.BooleanSupplier cancelled,Callable<T> work) throws Exception {
        try {if(wish!=null&&!wish.isBlank())WISH.set(new com.capybee.engine.input.PiiScrubber().normalize(wish));CANCELLED.set(cancelled);return work.call();}finally{WISH.remove();CANCELLED.remove();}
    }
    public static TextGenerator filter(TextGenerator provider) {
        return request->{if(CANCELLED.get()!=null&&CANCELLED.get().getAsBoolean())throw new IllegalStateException("PACK_START_CANCELLED");var response=provider.generate(request);String wish=WISH.get();
            if(wish!=null){
                boolean echo;
                try {echo=contains(SessionJson.tree(response.outputJson()),wish);}catch(RuntimeException invalid){echo=true;}
                if(echo)return new TextGenerationResult("{}",response.usage(),response.modelId(),response.finishReason());
            }
            return response;
        };
    }
    private static boolean contains(tools.jackson.databind.JsonNode node,String wish){
        if(node.isString())return node.asString().contains(wish);
        if(node.isObject())for(var property:node.properties())if(contains(property.getValue(),wish))return true;
        if(node.isArray())for(var child:node)if(contains(child,wish))return true;
        return false;
    }
}
