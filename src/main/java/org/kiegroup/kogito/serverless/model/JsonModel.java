package org.kiegroup.kogito.serverless.model;

import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;

import org.kie.kogito.Model;

public class JsonModel implements Model {

    public static final String DATA_PARAM = "data";

    private String id;
    private JsonObject data;

    public JsonModel setId(String id) {
        this.id = id;
        return this;
    }

    public String getId() {
        return id;
    }

    public JsonObject getData() {
        return data;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> params = new HashMap<>();
        params.put(DATA_PARAM, data);
        return params;
    }

    @Override
    public void fromMap(Map<String, Object> params) {
        this.id = null;
        this.data = (JsonObject) params.get(DATA_PARAM);
    }

    public void fromMap(String id, Map<String, Object> params) {
        this.id = id;
        this.data = (JsonObject) params.get(DATA_PARAM);
    }

    public static JsonModel newInstance(JsonObject data) {
        JsonModel jsonModel = new JsonModel();
        if(data == null) {
            data = JsonObject.EMPTY_JSON_OBJECT;
        }
        jsonModel.data = data;
        return jsonModel;
    }

}
