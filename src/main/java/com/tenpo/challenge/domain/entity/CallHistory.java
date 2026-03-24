package com.tenpo.challenge.domain.entity;

import com.tenpo.challenge.domain.enums.CallStatus;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("call_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CallHistory {

    @Id
    private Long id;

    @Column("endpoint")
    private String endpoint;

    @Column("http_method")
    private String httpMethod;

    @Column("request_params")
    private String requestParams;

    @Column("response_body")
    private String responseBody;

    @Column("error_message")
    private String errorMessage;

    @Column("http_status")
    private Integer httpStatus;

    @Column("status")
    private CallStatus status;

    @Column("client_ip")
    private String clientIp;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;
}
