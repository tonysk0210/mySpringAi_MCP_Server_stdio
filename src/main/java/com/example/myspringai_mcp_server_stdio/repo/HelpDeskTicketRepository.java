package com.example.myspringai_mcp_server_stdio.repo;

import com.example.myspringai_mcp_server_stdio.entity.HelpDeskTicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HelpDeskTicketRepository extends JpaRepository<HelpDeskTicketEntity, Long> {
    
    // 根據用戶名查詢 HelpDeskTicket Derived Query
    List<HelpDeskTicketEntity> findByUsername(String username);
}
