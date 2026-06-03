package dev.sbox.panel.service;

import dev.sbox.panel.repository.NodeRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.Comparator;
import java.util.Random;

@Service
public class PortAllocator {

    private final NodeRepository nodeRepository;
    private final Random random = new Random();

    public PortAllocator(NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    public int allocate(int rangeStart, int rangeEnd, int blockSize) {
        if (rangeStart < 100 || rangeEnd > 65535 || rangeStart >= rangeEnd || blockSize <= 0) {
            throw new IllegalArgumentException("端口范围配置无效");
        }
        if (rangeEnd - rangeStart + 1 < blockSize) {
            throw new IllegalArgumentException("端口范围不足以容纳一个端口块");
        }

        int maxStart = rangeEnd - blockSize + 1;
        for (int i = 0; i < 200; i++) {
            int candidate = rangeStart + random.nextInt(maxStart - rangeStart + 1);
            if (isBlockAvailable(candidate, blockSize)) {
                return candidate;
            }
        }

        for (int candidate = rangeStart; candidate <= maxStart; candidate++) {
            if (isBlockAvailable(candidate, blockSize)) {
                return candidate;
            }
        }
        throw new IllegalStateException("没有找到连续可用端口块");
    }

    private boolean isBlockAvailable(int startPort, int blockSize) {
        boolean overlapsDb = nodeRepository.findPortOwners().stream()
                .sorted(Comparator.comparingInt(node -> node.getStartPort()))
                .anyMatch(node -> rangesOverlap(startPort, startPort + blockSize - 1,
                        node.getStartPort(), node.getStartPort() + node.getPortBlockSize() - 1));
        if (overlapsDb) {
            return false;
        }
        for (int port = startPort; port < startPort + blockSize; port++) {
            if (!isTcpAvailable(port) || !isUdpAvailable(port)) {
                return false;
            }
        }
        return true;
    }

    private static boolean rangesOverlap(int leftStart, int leftEnd, int rightStart, int rightEnd) {
        return leftStart <= rightEnd && rightStart <= leftEnd;
    }

    private static boolean isTcpAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(false);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static boolean isUdpAvailable(int port) {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            socket.setReuseAddress(false);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }
}
