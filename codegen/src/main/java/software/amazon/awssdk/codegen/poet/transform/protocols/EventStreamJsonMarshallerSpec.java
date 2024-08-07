/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.codegen.poet.transform.protocols;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import javax.lang.model.element.Modifier;
import software.amazon.awssdk.codegen.model.intermediate.MemberModel;
import software.amazon.awssdk.codegen.model.intermediate.ShapeModel;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.protocols.core.OperationInfo;
import software.amazon.awssdk.protocols.core.ProtocolMarshaller;

/**
 * MarshallerSpec for event shapes in Json protocol
 */
public final class EventStreamJsonMarshallerSpec extends JsonMarshallerSpec {

    public EventStreamJsonMarshallerSpec(ShapeModel shapeModel) {
        super(shapeModel);
    }

    @Override
    public CodeBlock marshalCodeBlock(ClassName requestClassName) {
        String variableName = shapeModel.getVariable().getVariableName();
        CodeBlock.Builder builder =
            CodeBlock.builder()
                     .addStatement("$T<$T> protocolMarshaller = protocolFactory.createProtocolMarshaller(SDK_OPERATION_BINDING)",
                                   ProtocolMarshaller.class, SdkHttpFullRequest.class)
                     .add("return protocolMarshaller.marshall($L).toBuilder()", variableName)
                     .add(".putHeader($S, $S)", ":message-type", "event")
                     .add(".putHeader($S, $N.sdkEventType().toString())", ":event-type", variableName);

        // Add :content-type header only if payload is present
        if (!shapeModel.hasNoEventPayload()) {
            builder.add(".putHeader(\":content-type\", $L)", determinePayloadContentType());
        }

        builder.add(".build();");

        return builder.build();
    }

    @Override
    protected FieldSpec operationInfoField() {
        CodeBlock.Builder builder =
            CodeBlock.builder()
                     .add("$T.builder()", OperationInfo.class)
                     .add(".hasExplicitPayloadMember($L)", shapeModel.isHasPayloadMember() ||
                                                           shapeModel.getExplicitEventPayloadMember() != null)
                     .add(".hasPayloadMembers($L)", shapeModel.hasPayloadMembers())
                     .add(".hasImplicitPayloadMembers($L)", shapeModel.hasImplicitEventPayloadMembers())
                     // Adding httpMethod to avoid validation failure while creating the SdkHttpFullRequest
                     .add(".httpMethod($T.GET)", SdkHttpMethod.class)
                     .add(".hasEvent(true)")
                     .add(".build()");


        return FieldSpec.builder(ClassName.get(OperationInfo.class), "SDK_OPERATION_BINDING")
                        .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                        .initializer(builder.build())
                        .build();
    }

    private String determinePayloadContentType() {
        MemberModel explicitEventPayload = shapeModel.getExplicitEventPayloadMember();
        if (explicitEventPayload != null) {
            return getPayloadContentType(explicitEventPayload);
        }

        return "protocolFactory.getContentType()";
    }

    private String getPayloadContentType(MemberModel memberModel) {
        String blobContentType = "\"application/octet-stream\"";
        String stringContentType = "\"text/plain\"";
        String variableType = memberModel.getVariable().getVariableType();

        if ("software.amazon.awssdk.core.SdkBytes".equals(variableType)) {
            return blobContentType;
        } else if ("String".equals(variableType)) {
            return stringContentType;
        }

        return "protocolFactory.getContentType()";
    }
}
