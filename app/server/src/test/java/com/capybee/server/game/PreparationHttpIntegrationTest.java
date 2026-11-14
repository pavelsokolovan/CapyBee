package com.capybee.server.game;

import com.capybee.server.CapyBeeApplication;
import com.capybee.server.domain.user.UserAccount;
import com.capybee.server.domain.profile.FamilyProfile;
import com.capybee.server.repository.UserAccountRepository;
import com.capybee.server.repository.FamilyProfileRepository;
import com.capybee.engine.api.Engine;
import com.capybee.engine.pack.*;
import com.capybee.engine.pack.json.PackLoader;
import com.capybee.engine.port.*;
import com.capybee.engine.session.wire.SessionJson;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** Real HTTP, production controllers/coordinator/JDBC, isolated PostgreSQL schema; no paid calls. */
@EnabledIfEnvironmentVariable(named="CI", matches="true")
@SpringBootTest(classes=CapyBeeApplication.class,webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,
 properties={"server.address=127.0.0.1","capybee.game.enabled=true","capybee.game.web-client-enabled=true",
 "capybee.game.generation-enabled=true","capybee.game.dynamic.enabled=true","capybee.game.dynamic.profile-ids=generic-adventure",
 "capybee.game.command-hmac-key=preparation-fixture-key-32-bytes-long","capybee.game.sync-wait-ms=0",
 "capybee.game.dynamic.starts-per-hour=100","spring.session.jdbc.initialize-schema=never"})
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Import(PreparationHttpIntegrationTest.FixtureConfiguration.class)
class PreparationHttpIntegrationTest {
    static final int HTTP_PORT=freePort();
    static int freePort(){try(var socket=new java.net.ServerSocket(0)){return socket.getLocalPort();}catch(IOException failure){throw new IllegalStateException("FIXTURE_PORT_UNAVAILABLE",failure);}}
    static final String SCHEMA="prep_http_"+UUID.randomUUID().toString().replace("-", "");
    static final AtomicInteger BLUEPRINT_CALLS=new AtomicInteger();
    static volatile CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
    @LocalServerPort int port;
    @Autowired UserAccountRepository users;
    @Autowired FamilyProfileRepository profiles;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @TestBean(name="gameTextGenerator",methodName="provider") TextGenerator generator;
    @TestBean(name="gamePackRegistry",methodName="packs") PackRegistry registry;
    static TextGenerator provider() throws IOException {
        String blueprint=Files.readString(Path.of("../engine/certification/dynamic-v1/blueprint.json"));
        return request->{
            if(request.purpose()==StructuredGenerationRequest.Purpose.GAME_BLUEPRINT){
                BLUEPRINT_CALLS.incrementAndGet();entered.countDown();
                try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("FIXTURE_TIMEOUT");}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("FIXTURE_INTERRUPTED");}
            }
            return new TextGenerationResult(request.purpose()==StructuredGenerationRequest.Purpose.GAME_BLUEPRINT?blueprint:"{}",
                    new TextGenerationResult.TokenUsage(1,1,0,0),"preparation-fixture",TextGenerationResult.FinishReason.COMPLETE);
        };
    }
    static PackRegistry packs(){return new PackRegistry(List.of(new PackLoader(Engine.sectionValidators()).load(
            new PackId("fixture-minimal"),new DirectoryPackSource(Path.of("../engine/fixtures/game-packs/fixture-minimal")))));}
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties){
        properties.add("server.port",()->HTTP_PORT);
        properties.add("app.cors.allowed-origins",()->"http://localhost:5173,http://127.0.0.1:"+HTTP_PORT);
        properties.add("spring.datasource.url",()->"jdbc:postgresql://localhost:5432/capybee?currentSchema="+SCHEMA);
        properties.add("spring.datasource.username",()->"capybee");properties.add("spring.datasource.password",()->"capybee");
        properties.add("spring.flyway.schemas",()->SCHEMA);properties.add("spring.flyway.default-schema",()->SCHEMA);
    }
    @TestConfiguration(proxyBeanMethods=false) static class FixtureConfiguration {
        @Bean ClientRegistrationRepository fixtureRegistration(){return new InMemoryClientRegistrationRepository(
                CommonOAuth2Provider.GOOGLE.getBuilder("google").clientId("fixture").clientSecret("fixture").build());}
        @Bean @Order(0) SecurityFilterChain fixtureSecurity(HttpSecurity http) throws Exception {
            return http.securityMatcher("/**").csrf(csrf->csrf.disable()).authorizeHttpRequests(auth->auth.anyRequest().permitAll())
                    .addFilterBefore(new OncePerRequestFilter(){
                        @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
                            if("fixture".equals(request.getHeader("X-Preparation-Fixture"))){
                                var authority=new SimpleGrantedAuthority("ROLE_USER");
                                var principal=new DefaultOAuth2User(List.of(authority),Map.of("sub","preparation-fixture","email","fixture@example.invalid","name","Fixture"),"sub");
                                var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(new OAuth2AuthenticationToken(principal,List.of(authority),"google"));
                                SecurityContextHolder.setContext(context);
                            }
                            try{chain.doFilter(request,response);}finally{SecurityContextHolder.clearContext();}
                        }
                    },AnonymousAuthenticationFilter.class).build();
        }
    }
    HttpResponse<String> request(String method,String path,Object body) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(15))
                .header("X-Preparation-Fixture","fixture").header("Origin","http://localhost:5173").header("Content-Type","application/json");
        builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(SessionJson.encode(body)));
        return HttpClient.newBuilder().build().send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    tools.jackson.databind.JsonNode awaitStatus(String path,String terminal) throws Exception {
        for(int i=0;i<200;i++){
            var response=request("GET",path,null);var value=SessionJson.tree(response.body());
            if(terminal.equals(value.path("status").asString()))return value;
            if(response.statusCode()>=400)throw new AssertionError("HTTP fixture failed: "+response.statusCode()+" code="+value.path("code").asString()+" error="+value.path("error").path("code").asString());
            Thread.sleep(25);
        }
        throw new AssertionError("HTTP fixture did not reach terminal state");
    }
    @Test void startsPersistsPollsReplaysAndCancelsThroughHttp() throws Exception {
        var user=new UserAccount();user.setGoogleSubject("preparation-fixture");user.setEmail("fixture@example.invalid");user.setDisplayName("Fixture");user.setAvatarUrl("");user=users.saveAndFlush(user);
        var child=new FamilyProfile();child.setParentUser(user);child.setNickname("Fixture");child.setBirthYear(Year.now().getValue()-12);child.setPreferredLocale("en");child.setHasSeenOnboarding(true);profiles.saveAndFlush(child);
        try{
            assertThat(request("GET","/api/game/capabilities",null).statusCode()).isEqualTo(200);
            String packId=UUID.randomUUID().toString();
            var packStart=request("POST","/api/game/runs",Map.of("id",packId,"packId","fixture-minimal","locale","pl","startInputs",Map.of("tone","calm")));
            assertThat(packStart.statusCode()).isIn(200,202);
            var packRun=awaitStatus("/api/game/runs/"+packId,"ACTIVE");assertThat(packRun.path("source").path("kind").asString()).isEqualTo("PACK");
            var profile=SessionJson.tree(request("GET","/api/game/generator-profiles/generic-adventure",null).body());
            String requestId=UUID.randomUUID().toString();
            Map<String,Object> start=new LinkedHashMap<>(Map.of("requestId",requestId,"profileId","generic-adventure","profileVersion",profile.path("version").asString(),
                    "profileFingerprint",profile.path("fingerprint").asString(),"locale","en","lengthPreset","short","challengePreset","gentle","confirmed",true,"disclosureRevision",1,"expectedCurrentRunId",packId));
            start.put("suggestion","A fictional woodland journey");
            var initial=request("POST","/api/game/dynamic-starts",start);assertThat(initial.statusCode()).isEqualTo(202);
            assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            assertThat(request("POST","/api/game/dynamic-starts",start).statusCode()).isEqualTo(202);
            release.countDown();
            var started=awaitStatus("/api/game/dynamic-starts/"+requestId,"STARTED");
            String runId=started.path("run").path("runId").asString();assertThat(BLUEPRINT_CALLS.get()).isEqualTo(1);
            var saved=SessionJson.tree(request("GET","/api/game/runs/"+runId,null).body());
            assertThat(saved.path("source").path("kind").asString()).isEqualTo("DYNAMIC");
            assertThat(request("DELETE","/api/game/dynamic-starts/"+requestId,null).body()).contains("STARTED");
            var restored = new JdbcSessionStore(jdbc,transactions,Clock.systemUTC()).find(UUID.fromString(runId),
                    new com.capybee.engine.session.SessionTypes.OwnerRef(user.getId().toString())).orElseThrow();
            var offlineEngine = Engine.builder(new PackRegistry(List.of())).clock(Clock.systemUTC())
                    .random(com.capybee.engine.adapter.Randoms.seeded(7)).ids(new com.capybee.engine.adapter.UuidIdSource()).build();
            assertThat(offlineEngine.resume(restored.snapshot()).ok()).isTrue();
            assertThat(restored.snapshot().dynamic().bundle().gameFingerprint()).isEqualTo(saved.path("source").path("gameFingerprint").asString());
            assertThat(BLUEPRINT_CALLS.get()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT snapshot::text FROM game_runs WHERE id = ?",String.class,UUID.fromString(runId))).doesNotContain("A fictional woodland journey");
            entered=new CountDownLatch(1);release=new CountDownLatch(1);String cancelledId=UUID.randomUUID().toString();
            start.put("requestId",cancelledId);start.put("expectedCurrentRunId",runId);
            assertThat(request("POST","/api/game/dynamic-starts",start).statusCode()).isEqualTo(202);assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            assertThat(request("DELETE","/api/game/dynamic-starts/"+cancelledId,null).body()).contains("CANCELLED");release.countDown();
            assertThat(SessionJson.tree(request("GET","/api/game/runs/current",null).body()).path("runId").asString()).isEqualTo(runId);
            // Optional real-browser check against this authenticated fixture host; never shipped in production.
            String browser=System.getenv("CAPYBEE_PREPARATION_BROWSER_CHECK");
            if(browser!=null){
                var process=new ProcessBuilder("node",browser,"http://127.0.0.1:"+port).redirectErrorStream(true).start();
                boolean finished=process.waitFor(90,TimeUnit.SECONDS);
                if(!finished)process.destroyForcibly();
                assertThat(finished).as("Browser fixture timeout").isTrue();
                String diagnostic=new String(process.getInputStream().readNBytes(8192),java.nio.charset.StandardCharsets.UTF_8);
                assertThat(process.exitValue()).withFailMessage("Browser fixture failed: %s",diagnostic).isZero();
                assertThat(diagnostic).contains("PASS:");
            }
        }finally{release.countDown();jdbc.execute("DROP SCHEMA IF EXISTS "+SCHEMA+" CASCADE");}
    }
}
