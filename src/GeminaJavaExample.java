import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class GeminaJavaExample {
    private static final String API_KEY = "== YOUR API KEY ==";
    private static final String CLIENT_ID = "== YOUR CLIENT KEY ==";
    
    private static final String GEMINA_API_URL = "https://api.gemina.co.il/v1";
    private static final String UPLOAD_IMAGE_URL = "/uploads";
    private static final String BUSINESS_DOCUMENTS_URL = "/business_documents";
    private static final String UPLOAD_WEB_IMAGE_URL = "/uploads/web";
    
    private static final String INVOICE_PATH = "invoice.png";
    private static final String INVOICE_WEB_URL = "== YOUR INVOICE URL ==";
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public static void main(String[] args) throws Exception {
        String invoiceId = "ex_id_" + UUID.randomUUID().toString();

        // Step I: Upload Image to the Gemina API
        WebResponse webResponse = uploadImage(INVOICE_PATH, invoiceId);
        // Alternative: Upload via URL
        // WebResponse webResponse = uploadWebImage(INVOICE_WEB_URL, invoiceId);

        switch (webResponse.getStatusCode()) {
            case 201:
                // Success - Move to second phase
                break;
            case 202:
                System.out.println("Image is already being processed. No need to upload again.");
                break;
            case 409:
                System.out.println("A prediction already exists for this image. No need to upload again.");
                break;
            default:
                System.out.println("Server returned an error. Operation failed:");
                printJson(webResponse.getData());
                return;
        }

        // Step II: Get Prediction
        do {
            webResponse = getPrediction(invoiceId);

            switch (webResponse.getStatusCode()) {
                case 202:
                    System.out.println("Image is still being processed. Sleeping for 1 second before the next attempt.");
                    TimeUnit.SECONDS.sleep(1);
                    break;
                case 404:
                    System.out.println("Can't find image. Let's give it 1 second to create before we try again...");
                    TimeUnit.SECONDS.sleep(1);
                    break;
                case 200:
                    System.out.println("Successfully retrieved Prediction for Invoice Image " + invoiceId + ":");
                    printJson(webResponse.getData());
                    System.out.println("The Prediction Object Data is stored in this object: " + webResponse.getPrediction());
                    break;
                default:
                    System.out.println("Failed to retrieve Prediction for Invoice Image " + invoiceId + ":");
                    printJson(webResponse.getData());
                    break;
            }
        } while (webResponse.getStatusCode() == 202 || webResponse.getStatusCode() == 404);
    }

    private static WebResponse uploadImage(String invoicePath, String invoiceId) throws Exception {
        String url = GEMINA_API_URL + UPLOAD_IMAGE_URL;
        String token = "Basic " + API_KEY;  // Mind the space between 'Basic' and the API KEY

        byte[] fileContent = Files.readAllBytes(new File(invoicePath).toPath());
        Map<String, Object> jsonData = new HashMap<>();
        jsonData.put("client_id", CLIENT_ID);
        jsonData.put("external_id", invoiceId);
        jsonData.put("use_llm", true);
        jsonData.put("file", Base64.getEncoder().encodeToString(fileContent));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", token)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(jsonData)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        return new WebResponse(
            response.statusCode(),
            response.body() != null ? objectMapper.readValue(response.body(), Map.class) : null,
            response.body() != null ? objectMapper.readValue(response.body(), Prediction.class) : null
        );
    }

    private static WebResponse uploadWebImage(String invoiceURL, String invoiceId) throws Exception {
        String url = GEMINA_API_URL + UPLOAD_WEB_IMAGE_URL;
        String token = "Basic " + API_KEY;

        Map<String, Object> jsonData = new HashMap<>();
        jsonData.put("client_id", CLIENT_ID);
        jsonData.put("external_id", invoiceId);
        jsonData.put("use_llm", true);
        jsonData.put("url", invoiceURL);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", token)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(jsonData)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        return new WebResponse(
            response.statusCode(),
            response.body() != null ? objectMapper.readValue(response.body(), Map.class) : null,
            response.body() != null ? objectMapper.readValue(response.body(), Prediction.class) : null
        );
    }

    private static WebResponse getPrediction(String imageId) throws Exception {
        String url = GEMINA_API_URL + BUSINESS_DOCUMENTS_URL + "/" + imageId;
        String token = "Basic " + API_KEY;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .header("Authorization", token)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        return new WebResponse(
            response.statusCode(),
            response.body() != null ? objectMapper.readValue(response.body(), Map.class) : null,
            response.body() != null ? objectMapper.readValue(response.body(), Prediction.class) : null
        );
    }

    private static void printJson(Map<String, Object> jsonData) {
        if (jsonData != null) {
            jsonData.forEach((key, value) -> System.out.println(key + ": " + value));
        } else {
            System.out.println("Received empty Json response.");
        }
    }
}

class WebResponse {
    private final int statusCode;
    private final Map<String, Object> data;
    private final Prediction prediction;

    public WebResponse(int statusCode, Map<String, Object> data, Prediction prediction) {
        this.statusCode = statusCode;
        this.data = data;
        this.prediction = prediction;
    }

    public int getStatusCode() { return statusCode; }
    public Map<String, Object> getData() { return data; }
    public Prediction getPrediction() { return prediction; }
}

class Coordinates {
    @JsonProperty("original")
    private List<List<Integer>> original;
    @JsonProperty("normalized")
    private List<List<Integer>> normalized;
    @JsonProperty("relative")
    private List<List<Double>> relative;

    // Getters and setters
    public List<List<Integer>> getOriginal() { return original; }
    public void setOriginal(List<List<Integer>> original) { this.original = original; }
    public List<List<Integer>> getNormalized() { return normalized; }
    public void setNormalized(List<List<Integer>> normalized) { this.normalized = normalized; }
    public List<List<Double>> getRelative() { return relative; }
    public void setRelative(List<List<Double>> relative) { this.relative = relative; }
}

abstract class GeneralValue<T> {
    @JsonProperty("coordinates")
    private Coordinates coordinates;
    @JsonProperty("confidence")
    private String confidence;
    @JsonProperty("value")
    private T value;

    // Getters and setters
    public Coordinates getCoordinates() { return coordinates; }
    public void setCoordinates(Coordinates coordinates) { this.coordinates = coordinates; }
    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }
    public T getValue() { return value; }
    public void setValue(T value) { this.value = value; }
}

class LongValue extends GeneralValue<Long> {}
class IntValue extends GeneralValue<Integer> {}
class DoubleValue extends GeneralValue<Double> {}
class StringValue extends GeneralValue<String> {}

@JsonIgnoreProperties(ignoreUnknown = true)
class Prediction {
    @JsonProperty("total_amount")
    private DoubleValue totalAmount;
    @JsonProperty("vat_amount")
    private DoubleValue vatAmount;
    @JsonProperty("created")
    private Date created;
    @JsonProperty("timestamp")
    private double timestamp;
    @JsonProperty("primary_document_type")
    private StringValue primaryDocumentType;
    @JsonProperty("external_id")
    private String externalId;
    @JsonProperty("currency")
    private StringValue currency;
    @JsonProperty("business_number")
    private IntValue businessNumber;
    @JsonProperty("issue_date")
    private StringValue issueDate;
    @JsonProperty("document_type")
    private StringValue documentType;
    @JsonProperty("expense_type")
    private StringValue expenseType;
    @JsonProperty("payment_method")
    private StringValue paymentMethod;
    @JsonProperty("document_number")
    private LongValue documentNumber;
    @JsonProperty("net_amount")
    private DoubleValue netAmount;
    @JsonProperty("supplier_name")
    private StringValue supplierName;

    // Getters and setters
    public DoubleValue getTotalAmount() { return totalAmount; }
    public void setTotalAmount(DoubleValue totalAmount) { this.totalAmount = totalAmount; }
    public DoubleValue getVatAmount() { return vatAmount; }
    public void setVatAmount(DoubleValue vatAmount) { this.vatAmount = vatAmount; }
    public Date getCreated() { return created; }
    public void setCreated(Date created) { this.created = created; }
    public double getTimestamp() { return timestamp; }
    public void setTimestamp(double timestamp) { this.timestamp = timestamp; }
    public StringValue getPrimaryDocumentType() { return primaryDocumentType; }
    public void setPrimaryDocumentType(StringValue primaryDocumentType) { this.primaryDocumentType = primaryDocumentType; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public StringValue getCurrency() { return currency; }
    public void setCurrency(StringValue currency) { this.currency = currency; }
    public IntValue getBusinessNumber() { return businessNumber; }
    public void setBusinessNumber(IntValue businessNumber) { this.businessNumber = businessNumber; }
    public StringValue getIssueDate() { return issueDate; }
    public void setIssueDate(StringValue issueDate) { this.issueDate = issueDate; }
    public StringValue getDocumentType() { return documentType; }
    public void setDocumentType(StringValue documentType) { this.documentType = documentType; }
    public StringValue getExpenseType() { return expenseType; }
    public void setExpenseType(StringValue expenseType) { this.expenseType = expenseType; }
    public StringValue getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(StringValue paymentMethod) { this.paymentMethod = paymentMethod; }
    public LongValue getDocumentNumber() { return documentNumber; }
    public void setDocumentNumber(LongValue documentNumber) { this.documentNumber = documentNumber; }
    public DoubleValue getNetAmount() { return netAmount; }
    public void setNetAmount(DoubleValue netAmount) { this.netAmount = netAmount; }
    public StringValue getSupplierName() { return supplierName; }
    public void setSupplierName(StringValue supplierName) { this.supplierName = supplierName; }
}