package com.example.myspringai_mcp_server_stdio.service;

import com.example.myspringai_mcp_server_stdio.entity.HelpDeskTicketEntity;
import com.example.myspringai_mcp_server_stdio.payload.HelpDeskTicketPayload;
import com.example.myspringai_mcp_server_stdio.repo.HelpDeskTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HelpDeskTicketService {

    private final HelpDeskTicketRepository helpDeskTicketRepository;

    /**
     * 建立一個 HelpDeskTicket
     */
    public HelpDeskTicketEntity createHelpDeskTicket(HelpDeskTicketPayload payload) {
        // 1. 建立一個 HelpDeskTicketEntity
        HelpDeskTicketEntity ticket = HelpDeskTicketEntity.builder()
                .issue(payload.issue())
                .username(payload.username())
                .status("OPEN")
                .createdAt(LocalDateTime.now())
                .eta(LocalDateTime.now().plusDays(7))
                .build();
        // 2. 儲存 HelpDeskTicketEntity
        return helpDeskTicketRepository.save(ticket);
    }

    /**
     * 根據用戶名查詢 HelpDeskTicket
     */
    public List<HelpDeskTicketEntity> getHelpDeskTicketsByUser(String username) {
        return helpDeskTicketRepository.findByUsername(username);
    }
}
