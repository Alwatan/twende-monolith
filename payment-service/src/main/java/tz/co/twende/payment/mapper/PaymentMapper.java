package tz.co.twende.payment.mapper;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tz.co.twende.payment.dto.response.TransactionDto;
import tz.co.twende.payment.dto.response.WalletEntryDto;
import tz.co.twende.payment.entity.Transaction;
import tz.co.twende.payment.entity.WalletEntry;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(source = "id", target = "id")
    TransactionDto toTransactionDto(Transaction transaction);

    WalletEntryDto toWalletEntryDto(WalletEntry entry);

    List<WalletEntryDto> toWalletEntryDtoList(List<WalletEntry> entries);
}
