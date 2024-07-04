package org.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
  private static final String CRPT_API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
  private final TimeUnit timeUnit;
  private final int requestLimit;
  private final ObjectMapper objectMapper;
  private final Lock lock;
  private final Condition condition;
  private final ConcurrentLinkedDeque<LocalDateTime> requestTimes;

  public CrptApi(TimeUnit timeUnit, int requestLimit) {
    Objects.requireNonNull(timeUnit);
    if (requestLimit <= 0) {
      throw new IllegalArgumentException("Request limit should be positive");
    }
    this.requestLimit = requestLimit;
    this.timeUnit = timeUnit;
    this.objectMapper = new ObjectMapper();
    this.lock = new ReentrantLock();
    this.condition = lock.newCondition();
    this.requestTimes = new ConcurrentLinkedDeque<>();
  }

  public void createDocument(CrptDocument document, String signature) {
    lock.lock();
    try {
      while (!isRequestAvailable()) {
        condition.await();
      }
      sendCreateDocumentRequest(document, signature);
      requestTimes.add(LocalDateTime.now());
      condition.signalAll();
    } catch (Exception e) {
      System.out.println(e.getMessage());
    } finally {
      lock.unlock();
    }
  }

  private boolean isRequestAvailable() {
    LocalDateTime now = LocalDateTime.now();

    while (!requestTimes.isEmpty() && Duration.between(requestTimes.getFirst(), now).toNanos() >= timeUnit.toNanos(1)) {
      requestTimes.pollFirst();
    }

    return requestTimes.size() < requestLimit;
  }

  private void sendCreateDocumentRequest(CrptDocument document, String signature) throws IOException {
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      HttpPost httpPost = new HttpPost(CRPT_API_URL);

      String json = objectMapper.writeValueAsString(document);
      StringEntity entity = new StringEntity(json);

      httpPost.setEntity(entity);
      httpPost.setHeader("Content-Type", "application/json");
      httpPost.setHeader("X-Signature", signature);

      HttpClientResponseHandler<String> responseHandler = response -> {
        int status = response.getCode();
        if (status >= 200 && status < 300) {
          return EntityUtils.toString(response.getEntity());
        } else {
          throw new HttpException("Unexpected response status: " + status);
        }
      };

      String responseBody = httpClient.execute(httpPost, responseHandler);
      System.out.println(responseBody);

    }
  }

  private static class CrptDocument {
    @JsonProperty("description")
    private Description description;

    @JsonProperty("doc_id")
    private String docId;

    @JsonProperty("doc_status")
    private String docStatus;

    @JsonProperty("doc_type")
    private String docType;

    @JsonProperty("importRequest")
    private boolean importRequest;

    @JsonProperty("owner_inn")
    private String ownerInn;

    @JsonProperty("participant_inn")
    private String participantInn;

    @JsonProperty("producer_inn")
    private String producerInn;

    @JsonProperty("production_date")
    private String productionDate;

    @JsonProperty("production_type")
    private String productionType;

    @JsonProperty("products")
    private List<Product> products;

    @JsonProperty("reg_date")
    private String regDate;

    @JsonProperty("reg_number")
    private String regNumber;
  }

  private static class Description {
    @JsonProperty("participantInn")
    private String participantInn;
  }

  private static class Product {
    @JsonProperty("certificate_document")
    private String certificateDocument;

    @JsonProperty("certificate_document_date")
    private String certificateDocumentDate;

    @JsonProperty("certificate_document_number")
    private String certificateDocumentNumber;

    @JsonProperty("owner_inn")
    private String ownerInn;

    @JsonProperty("producer_inn")
    private String producerInn;

    @JsonProperty("production_date")
    private String productionDate;

    @JsonProperty("tnved_code")
    private String tnvedCode;

    @JsonProperty("uit_code")
    private String uitCode;

    @JsonProperty("uitu_code")
    private String uituCode;
  }

  public static void main(String[] args) {

    CrptApi crptApi = new CrptApi(TimeUnit.MINUTES, 8);

    CrptDocument document = new CrptDocument();
    String signature = "signature";

    crptApi.createDocument(document, signature);
  }

}