package com.yuwen.visionspace.model.dto.storage;

import com.yuwen.visionspace.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
public class StorageConfigQueryRequest extends PageRequest implements Serializable {

    private Long id;

    private String platform;

    private Integer status;

    private static final long serialVersionUID = 1L;
}