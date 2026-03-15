package com.chaykin.jasper.processor.model;

import java.util.List;

public record JrxmlDataset(String name,
                           List<JrxmlDatasetField> fields) {

}