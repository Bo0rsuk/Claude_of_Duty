package com.claudeofduty.core.port;

/** A language model that answers with a JSON document. */
public interface TextModel {

    /**
     * @param instructions what the model should do and the shape of its answer
     * @param input        the material to work on
     * @return the model's answer, expected to be JSON text
     * @throws RuntimeException when the model cannot be reached or refuses
     */
    String completeJson(String instructions, String input);
}
