package com.networkproxy.service;

import com.networkproxy.dto.ClientSummary;
import com.networkproxy.dto.PageResponse;
import com.networkproxy.repository.ClientRepository;
import org.springframework.stereotype.Service;

@Service
public class ClientService {

    private final ClientRepository clientRepository;

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    public PageResponse<ClientSummary> list(String q, int page, int size) {
        return clientRepository.findPage(
                q,
                Pagination.page(page),
                Pagination.size(size));
    }
}
