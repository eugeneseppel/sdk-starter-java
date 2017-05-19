package com.twilio;

import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.redirect;
import static spark.Spark.staticFileLocation;
import static spark.Spark.afterAfter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.twilio.base.ResourceSet;
import com.twilio.rest.notify.v1.service.Binding.BindingType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.github.javafaker.Faker;
import com.google.gson.Gson;

import com.twilio.rest.notify.v1.service.BindingCreator;
import com.twilio.rest.notify.v1.service.Binding;
import com.twilio.rest.notify.v1.service.Notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;




public class ServerApp {

    final static Logger logger = LoggerFactory.getLogger(ServerApp.class);

    static class UserBinding {
        public UserBinding(String type) {
            this.type = type;
        }
        public String type;
        public String status = "Not registered";
        public Boolean offers = false;
    }

    static class User {
        public User(String username) {
            this.username = username;
            bindings.add(new UserBinding(BindingType.APN.toString()));
            bindings.add(new UserBinding(BindingType.SMS.toString()));
            bindings.add(new UserBinding(BindingType.FACEBOOK_MESSENGER.toString()));
        }

        public String username;
        public List<UserBinding> bindings = new LinkedList<>();
        public String preferred = "";
    }

    private static class BindingConfigRequest {
        String type;
        String address;
        boolean acceptOffers;
    }

    private static class MessageRequest {
        String text;
        boolean preferred = false;
    }

    private static class BindingRequest {
        String endpoint;
        String identity;
        String BindingType;
        String Address;
    }

    private static class UserConfigRequest {
        String preferred;
    }

    //{entry: [ {messaging: [message: {sender: {id: "XXXX"} } ] } ] }
    /*private static class FaceBookAuthRequest {
        static class sender {

        }

        List<> lst;
    }*/


    private static class StandartResponse {
        String message;
        String error;
    }

    private static class SendNotificationResponse {
        String message;
        String error;
    }

    static final String preferred= "preferred";
    static final String marketingEnabled="marketingEnabled";

    public static void main(String[] args) {

        // Serve static files from src/main/resources/public
        staticFileLocation("/public");

        // Create a Faker instance to generate a random username for the connecting user
        Faker faker = new Faker();

        // Set up configuration from environment variables
        Map<String, String> configuration = new HashMap<>();
        configuration.put("TWILIO_ACCOUNT_SID", System.getenv("TWILIO_ACCOUNT_SID"));
        configuration.put("TWILIO_API_KEY", System.getenv("TWILIO_API_KEY"));
        configuration.put("TWILIO_API_SECRET", System.getenv("TWILIO_API_SECRET"));
        configuration.put("TWILIO_NOTIFICATION_SERVICE_SID", System.getenv("TWILIO_NOTIFICATION_SERVICE_SID"));
        configuration.put("TWILIO_CONFIGURATION_SID",System.getenv("TWILIO_CONFIGURATION_SID"));
        configuration.put("TWILIO_CHAT_SERVICE_SID",System.getenv("TWILIO_CHAT_SERVICE_SID"));
        configuration.put("TWILIO_SYNC_SERVICE_SID",System.getenv("TWILIO_SYNC_SERVICE_SID"));
        configuration.put("TWILIO_AUTH_TOKEN",System.getenv("TWILIO_AUTH_TOKEN"));

        Twilio.init(configuration.get("TWILIO_ACCOUNT_SID"), configuration.get("TWILIO_AUTH_TOKEN"));
        // Log all requests and responses
        afterAfter(new LoggingFilter());

        redirect.get("/users/*", "/");

        get("/api/users", "application/json", (request, response) -> {
            logger.debug(request.body());
            // List the bindings
            Map<String, User> userDict = getUserMap(configuration);

            response.type("application/json");
            Gson gson = new Gson();
            return gson.toJson(userDict.keySet().toArray());
        });

        get("/api/messenger_auth", "application/json", (request, response) -> {
            return request.queryParams("hub.challenge");
        });

        get("/api/users/:id", "application/json", (request, response) -> {
            logger.debug(request.body());
            // List the bindings
            Map<String, User> userDict = getUserMap(configuration);

            response.type("application/json");
            Gson gson = new Gson();

            final String id = request.params(":id");
            return gson.toJson(userDict.get(id));

            //return gson.toJson(userDict.values().toArray());
        });

        post("/api/users/:id/config", (request, response) -> {

            logger.debug(request.body());
            Gson gson = new Gson();
            StandartResponse standartResponse = new StandartResponse();
            int statusCode = 500;
            try {
                UserConfigRequest configRequest = gson.fromJson(request.body(), UserConfigRequest.class);

                ResourceSet<Binding> bindings = Binding.reader(configuration.get("TWILIO_NOTIFICATION_SERVICE_SID"))
                        .setIdentity(request.params(":id"))
                        .read();

                for (Binding binding : bindings) {
                    List<String> tags = binding.getTags();
                    // selected binding
                    if (binding.getBindingType().equals(configRequest.preferred)) {
                        if (!tags.contains(preferred)) {
                            // not preferred, set this binding as preferred
                            tags.add(preferred);
                            Binding newBinding = Binding.creator(
                                    configuration.get("TWILIO_NOTIFICATION_SERVICE_SID"),
                                    binding.getEndpoint(),
                                    binding.getIdentity(),
                                    Binding.BindingType.forValue(binding.getBindingType()),
                                    binding.getAddress())
                                    .setTag(tags)
                                    .create();
                        }
                    } else { // all other bindings
                        if (binding.getTags().contains(preferred)) {
                            tags.remove(preferred);
                            // is preferred, should be cleared
                            Binding newBinding = Binding.creator(
                                    configuration.get("TWILIO_NOTIFICATION_SERVICE_SID"),
                                    binding.getEndpoint(),
                                    binding.getIdentity(),
                                    Binding.BindingType.forValue(binding.getBindingType()),
                                    binding.getAddress())
                                    .setTag(tags)
                                    .create();
                        }
                    }
                }

                standartResponse.message = "OK " + request.params(":id") + " " + configRequest.preferred;
                response.type("application/json");
                return gson.toJson(standartResponse);
            } catch (com.twilio.exception.ApiException ex) {
                standartResponse.message = "Failed to config user: " + ex.getMessage();
                standartResponse.error = ex.getMessage();
                statusCode = ex.getStatusCode();
            } catch (Exception ex) {
                standartResponse.message = "Failed to config user: " + ex.getMessage();
                standartResponse.error = ex.getMessage();
            }
            logger.error("Exception configuring user: " + standartResponse.error );
            response.type("application/json");
            response.status(statusCode);
            return gson.toJson(standartResponse);
        });

        post("/api/register", (request, response) -> {
            logger.debug(request.body());

            // Decode the JSON Body
            Gson gson = new Gson();
            BindingRequest bindingRequest = gson.fromJson(request.body(), BindingRequest.class);

            // Create a binding
            Binding.BindingType bindingType = Binding.BindingType.forValue(bindingRequest.BindingType);
            BindingCreator creator = Binding.creator(configuration.get("TWILIO_NOTIFICATION_SERVICE_SID"),
                    bindingRequest.endpoint, bindingRequest.identity, bindingType, bindingRequest.Address);

            try {
                Binding binding = creator.create();
                logger.info("Binding successfully created");
                logger.debug(binding.toString());

                // Send a JSON response indicating success
                StandartResponse standartResponse = new StandartResponse();
                standartResponse.message = "Binding Created";
                response.type("application/json");
                return gson.toJson(standartResponse);

            } catch (Exception ex) {
                logger.error("Exception creating binding: " + ex.getMessage(), ex);

                // Send a JSON response indicating an error
                StandartResponse standartResponse = new StandartResponse();
                standartResponse.message = "Failed to create binding: " + ex.getMessage();
                standartResponse.error = ex.getMessage();
                response.type("application/json");
                response.status(500);
                return gson.toJson(standartResponse);
            }
        });

        post("/api/messenger_auth", "application/json", (request, response) -> {
            logger.debug(request.body());

            Gson gson = new Gson();
            StandartResponse standartResponse = new StandartResponse();
            int statusCode = 500;

            try {
                // {entry:[{messaging:[{sender:{id:"XXX"}, optin: {ref: "test"} }]}]}
                JsonArray messagingArray = new JsonParser().parse(request.body()).getAsJsonObject()
                        .getAsJsonArray("entry")
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonArray("messaging");

                JsonObject messageObj = messagingArray
                        .get(0)
                        .getAsJsonObject();

                String id = messageObj.get("sender").getAsJsonObject().get("id").getAsString();
                String user = messageObj.get("optin").getAsJsonObject().get("ref").getAsString();


                logger.info ("FACEBOOK ID= " + id + " User= " + user );
                // FACEBOOK ID= "facebook user, vasia555 "
                // we need identityUser, e.g. iphone6/test/gg/xxx

                removeSameBindings(configuration, id, BindingType.FACEBOOK_MESSENGER.toString());

                final Binding.BindingType bindingType = BindingType.FACEBOOK_MESSENGER;
                final String address = id;
                BindingCreator creator = Binding.creator(
                        configuration.get("TWILIO_NOTIFICATION_SERVICE_SID"),
                        bindingType + ":" + address + id,
                        user,
                        bindingType,
                        address);

                Binding binding = creator.create();

                logger.debug(binding.toString());

                standartResponse.message = "Facebook Binding successfully created";
                logger.info(standartResponse.message);

                response.type("application/json");
                return gson.toJson(standartResponse);
            } catch (com.twilio.exception.ApiException ex) {
                standartResponse.message = "Failed to register on Facebook: " + ex.getMessage();
                standartResponse.error = ex.getMessage();
                statusCode = ex.getStatusCode();
            } catch (Exception ex) {
                logger.error("x: ", ex);
                standartResponse.message = "Failed to register on Facebook: " + ex.getClass().toString();
                standartResponse.error = ex.getMessage();
            }
            logger.error(standartResponse.message);
            response.type("application/json");
            response.status(statusCode);
            return gson.toJson(standartResponse);
        });

        post("/api/users/:id/bindings", "application/json", (request, response) -> {
            logger.debug(request.body());

            Gson gson = new Gson();
            StandartResponse standartResponse = new StandartResponse();
            int statusCode = 500;
            try {
                final String identity = request.params(":id");
                // Send a JSON response indicating success

                BindingConfigRequest bindingConfigRequest = gson.fromJson(request.body(), BindingConfigRequest.class);

                final Binding.BindingType bindingType = Binding.BindingType.forValue(bindingConfigRequest.type);

                Binding previousBinding = null;

                final boolean smsBindingAndToRemove =
                        (bindingConfigRequest.type.equals(BindingType.SMS.toString()) && bindingConfigRequest.address != null);

                // for SMS we just delete old bindings of same type
                if (smsBindingAndToRemove) {
                    removeSameBindings(configuration, identity, bindingConfigRequest.type);
                }
                else  { // all other bindings
                    // Get previous binding
                    ResourceSet<Binding> bindings = Binding.reader(configuration.get("TWILIO_NOTIFICATION_SERVICE_SID"))
                            .setIdentity(identity).read();

                    for (Binding binding : bindings) {
                        logger.info("Found old binding: " + binding.getBindingType());
                        if (binding.getBindingType().equals(bindingConfigRequest.type)) {
                            previousBinding = binding;

                            break;
                        }
                    }
                }

                List<String> newTags = null;
                String newAddress = null;
                String newEndpoint = null;

                // configure values for new binding
                if (smsBindingAndToRemove) {
                    newTags = new LinkedList<>();
                    newAddress = bindingConfigRequest.address;
                    newEndpoint = bindingType + ":" + bindingConfigRequest.address + identity;
                }
                else {
                    if (previousBinding == null)
                        throw new java.lang.Exception("No binding to configure");
                    newAddress = previousBinding.getAddress();
                    newEndpoint = previousBinding.getEndpoint();
                    newTags = previousBinding.getTags();
                    if (newTags == null)
                        newTags = new LinkedList<>();
                }

                if (bindingConfigRequest.acceptOffers)
                    newTags.add("marketingEnabled");
                else
                    newTags.remove("marketingEnabled");

                // Create a binding
                BindingCreator creator = Binding.creator(
                        configuration.get("TWILIO_NOTIFICATION_SERVICE_SID"),
                        newEndpoint, identity, bindingType, newAddress)
                        .setTag(newTags);

                Binding binding = creator.create();
                logger.info("Binding successfully created");
                logger.debug(binding.toString());

                standartResponse.message = "OK " + identity
                        + " " + bindingConfigRequest.type + " " + bindingConfigRequest.address;

                response.type("application/json");

                return gson.toJson(standartResponse);

            } catch (com.twilio.exception.ApiException ex) {
                standartResponse.message = "Failed to create binding: " + ex.getMessage();
                standartResponse.error = ex.getMessage();
                statusCode = ex.getStatusCode();
            } catch (Exception ex) {
                standartResponse.message = "Failed to create binding: " + ex.getMessage();
                standartResponse.error = ex.getMessage();
            }
            logger.error(standartResponse.message);
            response.type("application/json");
            response.status(statusCode);
            logger.info("Code: " + statusCode);
            return gson.toJson(standartResponse);
        });

        post("/api/users/:id/message", (request, response) -> {
            logger.debug(request.body());
            Gson gson = new Gson();
            StandartResponse standartResponse = new StandartResponse();
            int statusCode = 500;
            try {
                // Get the identity
                final String identity = request.params(":id");

                MessageRequest messageRequest = gson.fromJson(request.body(), MessageRequest.class);

                standartResponse.message = "OK " + identity
                        + " " + messageRequest.text + " " + messageRequest.preferred;

                // Create the notification
                Notification notification = Notification
                        .creator(configuration.get("TWILIO_NOTIFICATION_SERVICE_SID"))
                        .setBody(messageRequest.text)
                        .setTag(messageRequest.preferred ? preferred : "all")
                        .setIdentity(identity)
                        .create();
                logger.info("Notification successfully created");
                //logger.debug(notification.toString());

                // Send a JSON response indicating success

                response.type("application/json");
                return new Gson().toJson(standartResponse);

            } catch (com.twilio.exception.ApiException ex) {
                standartResponse.message = "Failed to send message: " + ex.getMessage();
                standartResponse.error = ex.getMessage();
                statusCode = ex.getStatusCode();
            } catch (Exception ex) {
                standartResponse.message = "Failed to send message: " + ex.getMessage();
                standartResponse.error = ex.getMessage();
            }
            logger.error(standartResponse.message);
            response.type("application/json");
            response.status(statusCode);
            return gson.toJson(standartResponse);
        });

        // Get the configuration for variables for the health check
        /*
        get("/config", (request, response) -> {

            Map<String, Object> json = new HashMap<>();
            String apiSecret = configuration.get("TWILIO_API_SECRET");
            boolean apiSecretConfigured = (apiSecret != null) && !apiSecret.isEmpty();

            json.putAll(configuration);
            json.put("TWILIO_API_SECRET", apiSecretConfigured);


            // Render JSON response
            Gson gson = new Gson();
            response.type("application/json");
            return gson.toJson(json);
        });
        */

        /*post("/send-notification", (request, response) -> {
            try {
                // Get the identity
                String identity = request.raw().getParameter("identity");
                logger.info("Identity: " + identity);

                // Create the notification
                String serviceSid = configuration.get("TWILIO_NOTIFICATION_SERVICE_SID");
                Notification notification = Notification
                    .creator(serviceSid)
                    .setBody("Hello " + identity)
                    .setIdentity(identity)
                    .create();
                logger.info("Notification successfully created");
                logger.debug(notification.toString());

                // Send a JSON response indicating success
                SendNotificationResponse sendNotificationResponse = new SendNotificationResponse();
                sendNotificationResponse.message = "Notification Created";
                response.type("application/json");
                return new Gson().toJson(sendNotificationResponse);

            } catch (Exception ex) {
                logger.error("Exception creating notification: " + ex.getMessage(), ex);

                // Send a JSON response indicating an error
                SendNotificationResponse sendNotificationResponse = new SendNotificationResponse();
                sendNotificationResponse.message = "Failed to create notification: " + ex.getMessage();
                sendNotificationResponse.error = ex.getMessage();
                response.type("application/json");
                response.status(500);
                return new Gson().toJson(sendNotificationResponse);
            }
        });
        */
        // Create an access token using our Twilio credentials
        /*
        get("/token", "application/json", (request, response) -> {
            // Generate a random username for the connecting client
            String identity = faker.firstName() + faker.lastName() + faker.zipCode();

            // Create an endpoint ID which uniquely identifies the user on their current device
            String appName = "TwilioAppDemo";

            // Create access token builder
            AccessToken.Builder builder = new AccessToken.Builder(
                    configuration.get("TWILIO_ACCOUNT_SID"),
                    configuration.get("TWILIO_API_KEY"),
                    configuration.get("TWILIO_API_SECRET")
            ).identity(identity);

            List<Grant> grants = new ArrayList<>();

            // Add Sync grant if configured
            if (configuration.containsKey("TWILIO_SYNC_SERVICE_SID")) {
                SyncGrant grant = new SyncGrant();
                grant.setServiceSid(configuration.get("TWILIO_SYNC_SERVICE_SID"));
                grants.add(grant);
            }

            // Add Chat grant if configured
            if (configuration.containsKey("TWILIO_CHAT_SERVICE_SID")) {
                IpMessagingGrant grant = new IpMessagingGrant();
                grant.setServiceSid(configuration.get("TWILIO_CHAT_SERVICE_SID"));
                grants.add(grant);
            }

            // Add Video grant if configured
            if (configuration.containsKey("TWILIO_CONFIGURATION_SID")) {
                VideoGrant grant  = new VideoGrant();
                grant.setConfigurationProfileSid(configuration.get("TWILIO_CONFIGURATION_SID"));
                grants.add(grant);
            }

            builder.grants(grants);

            AccessToken token = builder.build();


            // create JSON response payload
            HashMap<String, String> json = new HashMap<String, String>();
            json.put("identity", identity);
            json.put("token", token.toJwt());

            // Render JSON response
            Gson gson = new Gson();
            response.type("application/json");
            return gson.toJson(json);
        });
        */
    }

    private static void removeSameBindings(Map<String, String> configuration,
            String identity,
            String bindingType) {
        ResourceSet<Binding> bindings = Binding.reader(configuration.get("TWILIO_NOTIFICATION_SERVICE_SID"))
                .setIdentity(identity)
                .read();

        for (Binding binding : bindings) {
            if (binding.getBindingType().equals(bindingType)) {
                logger.info("Delete binding: " + binding.getEndpoint());
                Binding.deleter(configuration.get("TWILIO_NOTIFICATION_SERVICE_SID"), binding.getSid()).delete();
            }
        }
    }

    private static Map<String, User> getUserMap(Map<String, String> configuration) {

        ResourceSet<Binding> bindings = Binding.reader(configuration.get("TWILIO_NOTIFICATION_SERVICE_SID")).read();

        Map<String, User> userDict = new Hashtable<>();
        for (Binding binding : bindings) {
            String username = binding.getIdentity();
            User user;
            if (!userDict.containsKey(username)) {
                user = new User(username);
                userDict.put(username, user);
            } else {
                user = userDict.get(username);
            }

            final String bindingType = binding.getBindingType();

            HashSet<String> tags = new HashSet<>(binding.getTags());
            if (tags.contains(preferred))
                user.preferred = bindingType;

            //TODO: rewrite it
            for (UserBinding ub : user.bindings) {
                if (ub.type.equals(bindingType)) {
                    ub.offers = tags.contains(marketingEnabled);
                    ub.status = (bindingType.equals("sms")) ? binding.getAddress() : "Registered";
                }
            }
        }
        return userDict;
    }
}