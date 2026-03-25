@Override
    @CustomerTransactional
    public List<OrderDto> saveOrdersBulk(String schemaName, List<OrderSaveAggregate> orderSaveAggregates) {
        if (orderSaveAggregates == null || orderSaveAggregates.isEmpty()) {
            return new ArrayList<>();
        }

        LocalDateTime now = LocalDateTime.now();
        List<OrderDto> savedOrders = new ArrayList<>();

        List<OrderSeqCreateVo> orderSeqCreateVos = orderSaveAggregates.stream()
                .map(aggregate -> OrderSeqCreateVo.from(now))
                .toList();
        
        // 메모리 이슈 가능성 1
        orderCustomerMapper.insertOrderSeqsBulk(schemaName, orderSeqCreateVos);

        List<OrderAddressCreateVo> orderAddressCreateVos = orderSaveAggregates.stream()
                .map(OrderSaveAggregate::getAddress)
                .toList();
        
        // 메모리 이슈 가능성 2
        orderCustomerMapper.insertOrderAddressesBulk(schemaName, orderAddressCreateVos);

        List<OrderCreateVo> orderCreateVos = new ArrayList<>();
        for (int i = 0; i < orderSaveAggregates.size(); i++) {
            OrderSaveAggregate aggregate = orderSaveAggregates.get(i);
            OrderCreateVo orderCreateVo = aggregate.getOrder();
            OrderSeqCreateVo orderSeqCreateVo = orderSeqCreateVos.get(i);
            OrderAddressCreateVo orderAddressCreateVo = orderAddressCreateVos.get(i);

            orderCreateVo.setCreateDate(now);
            orderCreateVo.setOrderDt(now);
            orderCreateVo.setOrderAddressId(orderAddressCreateVo.getOrderAddressId());
            orderCreateVo.setOrderCode(now, orderSeqCreateVo.getOrderSeq());
            orderCreateVos.add(orderCreateVo);
        }

        // 메모리 이슈 가능성 3 
        orderCustomerMapper.insertOrdersBulk(schemaName, orderCreateVos);

        List<OrderEtcCreateVo> orderEtcCreateVos = new ArrayList<>();
        for (int i = 0; i < orderSaveAggregates.size(); i++) {
            OrderSaveAggregate aggregate = orderSaveAggregates.get(i);
            OrderEtcCreateVo orderEtcCreateVo = aggregate.getEtc();
            orderEtcCreateVo.setOrderId(orderCreateVos.get(i).getOrderId()); // useGeneratedKeys로 설정된 ID 사용
            orderEtcCreateVos.add(orderEtcCreateVo);
        }
        
        // 메모리 이슈 가능성 4
        orderCustomerMapper.insertOrderEtcsBulk(schemaName, orderEtcCreateVos);

        List<OrderItemCreateVo> allOrderItemCreateVos = new ArrayList<>();
        for (int i = 0; i < orderSaveAggregates.size(); i++) {
            final int index = i; // effectively final 변수로 생성
            OrderSaveAggregate aggregate = orderSaveAggregates.get(i);
            List<OrderItemCreateVo> orderItemCreateVos = aggregate.getItems();
            orderItemCreateVos.forEach(item -> item.setOrderId(orderCreateVos.get(index).getOrderId())); // useGeneratedKeys로 설정된 ID 사용
            allOrderItemCreateVos.addAll(orderItemCreateVos);
        }

        if (!allOrderItemCreateVos.isEmpty()) {
            // 메모리 이슈 가능성 5
            orderItemCustomerMapper.insertOrderItems(schemaName, allOrderItemCreateVos);
        }

        List<OrderItemCdCreateVo> allOrderItemCdCreateVos = new ArrayList<>();
        for (OrderItemCreateVo orderItemCreateVo : allOrderItemCreateVos) {
            if (Objects.nonNull(orderItemCreateVo)) {
                OrderItemCdCreateVo orderItemCdCreateVo = orderItemCreateVo.getOrderItemCdCreateVo();
                orderItemCdCreateVo.updateOrderItemId(orderItemCreateVo.getOrderItemId());
                allOrderItemCdCreateVos.add(orderItemCdCreateVo);
            }
        }

        if (!allOrderItemCdCreateVos.isEmpty()) {
            // 메모리 이슈 가능성 6
            orderItemCustomerMapper.insertOrderItemCds(schemaName, allOrderItemCdCreateVos);
        }

        for (int i = 0; i < orderSaveAggregates.size(); i++) {
            OrderCreateVo orderCreateVo = orderCreateVos.get(i);
            List<OrderItemCreateVo> orderItemCreateVos = orderSaveAggregates.get(i).getItems();
            OrderDto orderDto = OrderDto.create(orderCreateVo, orderItemCreateVos);
            savedOrders.add(orderDto);
        }

        return savedOrders;
    }