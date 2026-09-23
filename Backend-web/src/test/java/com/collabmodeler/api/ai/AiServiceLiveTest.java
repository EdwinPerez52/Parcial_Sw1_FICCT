package com.collabmodeler.api.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "AI_LIVE_TEST", matches = "true")
class AiServiceLiveTest {
    @Test void extractsClassesAttributesAndCardinalitiesFromADiagramImage() throws Exception {
        var properties = new AiProperties();
        properties.setBaseUrl(required("AI_BASE_URL"));
        properties.setApiKey(required("AI_API_KEY"));
        properties.setVisionModel(required("AI_VISION_MODEL"));

        var proposal = new AiService(properties, new ObjectMapper(), RestClient.builder())
            .analyzeImage(sampleDiagram(), "image/png");

        assertThat(proposal.path("classes")).anySatisfy(value ->
            assertThat(value.path("name").asText()).isEqualToIgnoringCase("Cliente"));
        assertThat(proposal.path("classes")).anySatisfy(value ->
            assertThat(value.path("name").asText()).isEqualToIgnoringCase("Pedido"));
        assertThat(proposal.path("associations")).isNotEmpty();
        assertThat(proposal.path("associations").path(0).path("sourceCardinality").asText()).isIn("1", "0..1", "0..*", "1..*");
        assertThat(proposal.path("associations").path(0).path("targetCardinality").asText()).isIn("1", "0..1", "0..*", "1..*");
    }

    private byte[] sampleDiagram() throws Exception {
        BufferedImage image = new BufferedImage(1200, 700, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(Color.BLACK); graphics.setStroke(new BasicStroke(4));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
        drawClass(graphics, 100, 150, "Cliente", new String[]{"id: UUID {PK}", "nombre: String", "email: String"});
        drawClass(graphics, 750, 150, "Pedido", new String[]{"id: UUID {PK}", "fecha: Date", "total: Decimal"});
        graphics.drawLine(500, 300, 750, 300);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 30));
        graphics.drawString("1", 520, 285); graphics.drawString("0..*", 670, 285);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private void drawClass(Graphics2D graphics, int x, int y, String name, String[] attributes) {
        int width = 400; int header = 70; int row = 55; int height = header + attributes.length * row;
        graphics.drawRect(x, y, width, height); graphics.drawLine(x, y + header, x + width, y + header);
        graphics.drawString(name, x + 25, y + 46);
        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 28));
        for (int index = 0; index < attributes.length; index++) {
            graphics.drawString(attributes[index], x + 20, y + header + 40 + index * row);
        }
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " no está configurada");
        return value;
    }
}
