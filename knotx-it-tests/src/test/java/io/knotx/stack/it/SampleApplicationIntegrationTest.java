/*
 * Copyright (C) 2018 Knot.x Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.knotx.stack.it;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static io.knotx.junit5.wiremock.KnotxWiremockExtension.stubForServer;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.knotx.junit5.KnotxApplyConfiguration;
import io.knotx.junit5.KnotxExtension;
import io.knotx.junit5.RandomPort;
import io.knotx.junit5.wiremock.ClasspathResourcesMockServer;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import io.vertx.reactivex.core.Vertx;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(KnotxExtension.class)
@TestInstance(Lifecycle.PER_CLASS)
class SampleApplicationIntegrationTest {

  @ClasspathResourcesMockServer
  private WireMockServer mockService;

  @ClasspathResourcesMockServer
  private WireMockServer mockBrokenService;

  @ClasspathResourcesMockServer
  private WireMockServer mockRepository;

  @BeforeAll
  void initMocks() {
    stubForServer(mockService,
        get(urlMatching("/service/mock/.*"))
            .willReturn(
                aResponse()
                    .withHeader("Cache-control", "no-cache, no-store, must-revalidate")
                    .withHeader("Content-Type", "application/json; charset=UTF-8")
                    .withHeader("X-Server", "Knot.x")
            ));

    stubForServer(mockBrokenService,
        get(urlMatching("/service/broken/.*"))
            .willReturn(
                aResponse()
                    .withStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
            ));

    stubForServer(mockRepository,
        get(urlMatching("/content/.*"))
            .willReturn(
                aResponse()
                    .withHeader("Cache-control", "no-cache, no-store, must-revalidate")
                    .withHeader("Content-Type", "text/html; charset=UTF-8")
                    .withHeader("X-Server", "Knot.x")
            ));
  }

  @Test
  @KnotxApplyConfiguration("conf/application.conf")
  void requestFsRepoSimplePage(VertxTestContext context, Vertx vertx,
      @RandomPort Integer globalServerPort) {
    KnotxServerTester serverTester = KnotxServerTester.defaultInstance(globalServerPort);
    serverTester.testGetRequest(context, vertx, "/content/local/fullPage.html",
        "results/local-fullPage.html");
  }

  @Test
  @KnotxApplyConfiguration("conf/application.conf")
  void requestHttpRepoSimplePage(VertxTestContext context, Vertx vertx,
      @RandomPort Integer globalServerPort) {
    KnotxServerTester serverTester = KnotxServerTester.defaultInstance(globalServerPort);
    serverTester.testGetRequest(context, vertx, "/content/remote/fullPage.html",
        "results/remote-fullPage.html");
  }

  @Test
  @KnotxApplyConfiguration("conf/application.conf")
  void requestPageWithRequestParameters(VertxTestContext context, Vertx vertx,
      @RandomPort Integer globalServerPort) {
    KnotxServerTester serverTester = KnotxServerTester.defaultInstance(globalServerPort);
    serverTester.testGetRequest(context, vertx,
        "/content/remote/fullPage.html?parameter%20with%20space=value&q=knotx",
        "results/remote-fullPage.html");
  }

  @Test
  @KnotxApplyConfiguration("conf/application.conf")
  void requestPageWithServiceThatReturns500(VertxTestContext context, Vertx vertx,
      @RandomPort Integer globalServerPort) {
    KnotxServerTester serverTester = KnotxServerTester.defaultInstance(globalServerPort);
    serverTester.testGetRequest(context, vertx, "/content/local/brokenService.html",
        "results/brokenService.html");
  }

  @Test
  @Disabled
  @KnotxApplyConfiguration("conf/application.conf")
  void requestPageThatUseFormsDatabridgeAndTe(VertxTestContext context, Vertx vertx,
      @RandomPort Integer globalServerPort) {
    KnotxServerTester serverTester = KnotxServerTester.defaultInstance(globalServerPort);
    serverTester.testGetRequest(context, vertx, "/content/local/formsBridgeTe.html",
        "results/formsBridgeTe.html");
  }

  @Test
  @Disabled
  @KnotxApplyConfiguration("conf/application.conf")
  void submitOneFormAfterAnother(VertxTestContext context, Vertx vertx,
      @RandomPort Integer globalServerPort) {
    KnotxServerTester serverTester = KnotxServerTester.defaultInstance(globalServerPort);
    final JsonObject competitionFormData = new JsonObject()
        .put("name", "test")
        .put("email", "email-1@example.com")
        .put("_frmId", "competition");
    final JsonObject newsletterFormData = new JsonObject()
        .put("name", "test")
        .put("email", "email-2@example.com")
        .put("_frmId", "newsletter");

    mockFormsAdapter(vertx, competitionFormData, newsletterFormData);
    serverTester.testPostRequest(context, vertx, "/content/local/formsBridgeTe.html",
        competitionFormData.getMap(),
        "results/submitCompetitionForm.html");
    serverTester.testPostRequest(context, vertx, "/content/local/formsBridgeTe.html",
        newsletterFormData.getMap(),
        "results/submitNewsletterForm.html");
  }

  private void mockFormsAdapter(Vertx vertx, JsonObject competitionData,
      JsonObject newsletterData) {
//    ClientResponse clientResponse = new ClientResponse().setStatusCode(404);
//    FormsAdapterResponse resp = new FormsAdapterResponse().setResponse(clientResponse);
//
//    new ServiceBinder(vertx.getDelegate())
//        .setAddress("knotx.forms.mock.adapter")
//        .register(FormsAdapterProxy.class, (request, result) -> {
//          String path = request.getParams().getString("testedFormId");
//          if (StringUtils.isNotBlank(path)) {
//            if (path.equals("competitionForm")) {
//              clientResponse.setStatusCode(200)
//                  .setBody(new JsonObject().put("form", competitionData).toBuffer());
//            } else if (path.equals("newsletterForm")) {
//              clientResponse.setStatusCode(200)
//                  .setBody(new JsonObject().put("form", newsletterData).toBuffer());
//            }
//          }
//          result.handle(Future.succeededFuture(resp));
//        });
  }
}
