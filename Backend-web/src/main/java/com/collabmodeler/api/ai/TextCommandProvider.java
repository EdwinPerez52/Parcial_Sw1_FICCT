package com.collabmodeler.api.ai;

import com.collabmodeler.api.diagram.DiagramDocument;
import com.collabmodeler.api.diagram.DiagramOperationRequest;

public interface TextCommandProvider {
    String id();
    DiagramOperationRequest interpret(String instruction, DiagramDocument diagram);
}
