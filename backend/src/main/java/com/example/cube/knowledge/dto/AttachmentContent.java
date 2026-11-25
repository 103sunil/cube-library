package com.example.cube.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AttachmentContent {

    private final String fileName;
    private final String contentType;
    private final byte[] data;
}
