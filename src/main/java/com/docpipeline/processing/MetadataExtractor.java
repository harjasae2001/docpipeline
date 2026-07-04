package com.docpipeline.processing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;
import software.amazon.awssdk.services.textract.model.EntityType;
import software.amazon.awssdk.services.textract.model.Relationship;
import software.amazon.awssdk.services.textract.model.RelationshipType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class MetadataExtractor {

    private final ObjectMapper objectMapper;

    public MetadataExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String extractText(List<Block> blocks) {
        return blocks.stream()
                .filter(block -> block.blockType() == BlockType.LINE)
                .map(Block::text)
                .collect(Collectors.joining("\n"));
    }

    public Map<String, String> extractKeyValuePairs(List<Block> blocks) {
        Map<String, String> keyValuePairs = new HashMap<>();
        Map<String, Block> blockMap = new HashMap<>();

        for (Block block : blocks) {
            blockMap.put(block.id(), block);
        }

        List<Block> keyBlocks = blocks.stream()
                .filter(block -> block.blockType() == BlockType.KEY_VALUE_SET)
                .filter(block -> block.entityTypes() != null && block.entityTypes().contains(EntityType.KEY))
                .toList();

        for (Block keyBlock : keyBlocks) {
            String key = getTextFromBlock(keyBlock, blockMap);
            Block valueBlock = findValueBlock(keyBlock, blockMap);
            if (valueBlock != null) {
                String value = getTextFromBlock(valueBlock, blockMap);
                if (!key.isEmpty()) {
                    keyValuePairs.put(key, value);
                }
            }
        }

        return keyValuePairs;
    }

    public double calculateAverageConfidence(List<Block> blocks) {
        return blocks.stream()
                .filter(block -> block.confidence() != null)
                .mapToDouble(Block::confidence)
                .average()
                .orElse(0.0);
    }

    public String toJsonMetadata(String text, Map<String, String> kvPairs, double confidence) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("extractedText", text);
            metadata.put("keyValuePairs", kvPairs);
            metadata.put("averageConfidence", confidence);
            metadata.put("keyValuePairCount", kvPairs.size());
            metadata.put("textLength", text.length());
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize metadata to JSON", e);
            return "{}";
        }
    }

    private String getTextFromBlock(Block block, Map<String, Block> blockMap) {
        if (block.relationships() == null) {
            return "";
        }

        StringBuilder text = new StringBuilder();
        for (Relationship relationship : block.relationships()) {
            if (relationship.type() == RelationshipType.CHILD) {
                for (String childId : relationship.ids()) {
                    Block childBlock = blockMap.get(childId);
                    if (childBlock != null && childBlock.blockType() == BlockType.WORD) {
                        if (!text.isEmpty()) {
                            text.append(" ");
                        }
                        text.append(childBlock.text());
                    }
                }
            }
        }
        return text.toString();
    }

    private Block findValueBlock(Block keyBlock, Map<String, Block> blockMap) {
        if (keyBlock.relationships() == null) {
            return null;
        }

        for (Relationship relationship : keyBlock.relationships()) {
            if (relationship.type() == RelationshipType.VALUE) {
                for (String valueId : relationship.ids()) {
                    Block valueBlock = blockMap.get(valueId);
                    if (valueBlock != null) {
                        return valueBlock;
                    }
                }
            }
        }
        return null;
    }
}
