# Gemina API - Quick Implementation Guide - Java

It's fast and easy to implement the Gemina Invoice Analysis.

First, define the API key that you were given, as well as the Client Id:

```java
private static final String API_KEY = "== YOUR API KEY ==";
private static final String CLIENT_ID = "== YOUR CLIENT KEY ==";
```

Also define the Gemina URL and endpoints:

```java
private static final String GEMINA_API_URL = "https://api.gemina.co.il/v1";
private static final String UPLOAD_IMAGE_URL = "/uploads";
private static final String BUSINESS_DOCUMENTS_URL = "/business_documents";
```

If you use a web image (instead of uploading one), then set the URL of the invoice.
In addition, don't forget to update the upload URL to web.

```java
private static final String INVOICE_WEB_URL = "== YOUR INVOICE URL ==";
private static final String UPLOAD_WEB_IMAGE_URL = "/uploads/web";
```

Next, start implementing Gemina.
It happens in 2 steps:

------

## Step 1 - Upload Invoice

Here you upload a Business Document (for example: an invoice / credit invoice / receipt, and more) in an image format (we support all the available formats e.g. Jpeg / PNG / PDF).

The server will return the status code **201** to signify that the image has been added and that processing has started.

*If you use the same endpoint again*, you will find out that the server returns a *202 code*, to let you know that the same image has already been accepted, and there's no need to upload it again.

It could also return *409 if a prediction already exists for that image*.

Please note that the image file needs to be encoded as Base64 and then added to the json payload as "*file*".

```java
private static WebResponse uploadImage(String invoicePath, String invoiceId) throws Exception {
    String url = GEMINA_API_URL + UPLOAD_IMAGE_URL;
    String token = "Basic " + API_KEY;  // Mind the space between 'Basic' and the API KEY

    byte[] fileContent = Files.readAllBytes(new File(invoicePath).toPath());
    Map<String, Object> jsonData = new HashMap<>();
    jsonData.put("client_id", CLIENT_ID);
    jsonData.put("external_id", invoiceId);
    jsonData.put("file", Base64.getEncoder().encodeToString(fileContent));
    jsonData.put("use_llm", true);

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
```

**Alternatively,** you can submit an existing web image here:

```java
private static WebResponse uploadWebImage(String invoiceURL, String invoiceId) throws Exception {
    String url = GEMINA_API_URL + UPLOAD_WEB_IMAGE_URL;
    String token = "Basic " + API_KEY;

    Map<String, Object> jsonData = new HashMap<>();
    jsonData.put("client_id", CLIENT_ID);
    jsonData.put("external_id", invoiceId);
    jsonData.put("url", invoiceURL);
    jsonData.put("use_llm", true);

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
```

Here's how you use the above methods:

```java
// *** Step I: Upload Image to the Gemina API *** //
WebResponse webResponse = uploadImage(INVOICE_PATH, invoiceId);
// Alternatively - Provide an Image URL instead of uploading
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
```

------

## Step 2 - Get Prediction

Here you retrieve a prediction for the invoice that you uploaded during the first step.

**Important Update - Retrieve Prediction as Java Object:**
If you wish to retrieve the prediction as a Java Object (as opposed to a Json text), please follow the code example in the repository.

**Retrieve Prediction as Json:**
You have to wait until the document finished processing.
Therefore you need to keep asking the server when the prediction is ready.

When it's not yet ready, the server will return either 404 (not found) or 202 (accepted and in progress).
*When Ready, the server will return **200**, with the prediction payload*.

```java
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
```

```java
// *** Step II: Get Prediction from the Gemina API *** //
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
            break;
        default:
            System.out.println("Failed to retrieve Prediction for Invoice Image " + invoiceId + ":");
            printJson(webResponse.getData());
            break;
    }
} while (webResponse.getStatusCode() == 202 || webResponse.getStatusCode() == 404);
```

------

## Other Features

#### Pass the Client Tax Id

To facilitate the algorithm's work and increase accuracy, you can pass the Client's Tax Id to the API with each Json call.
This will help to avoid situations where the Client's Tax Id is mistakenly interpreted as the Supplier's Tax Id (or Business Number).

To do so, add the following line to the Map (that is, to your Json):

```java
jsonData.put("client_business_number", "== Your Client's Business Number ==");
```

------

Full example:

```java
Map<String, Object> jsonData = new HashMap<>();
jsonData.put("client_id", CLIENT_ID);
jsonData.put("external_id", invoiceId);
jsonData.put("client_business_number", "== Your Client's Business Number ==");
jsonData.put("file", Base64.getEncoder().encodeToString(fileContent));
jsonData.put("use_llm", true);
```

The `client_business_number` can be represented either by `String` or `Integer`.

------

## More Resources

Response Types - https://github.com/tommyil/gemina-examples/blob/master/response_types.md

Data Loop - https://github.com/tommyil/gemina-examples/blob/master/data_loop.md

LLM Integration - https://github.com/tommyil/gemina-examples/blob/master/llm_integration.md

C# Implementation - https://github.com/tommyil/gemina-examples-cs

Python Implementation - https://github.com/tommyil/gemina-examples

Node.js/TypeScript Implementation - https://github.com/tommyil/gemina-examples-ts

PHP Implementation - https://github.com/tommyil/gemina-examples-php

------

The full example code is available in this repository.

For more details, please refer to the [API documentation](https://api.gemina.co.il/swagger/).

You can also contact us [here](mailto:info@gemina.co.il).