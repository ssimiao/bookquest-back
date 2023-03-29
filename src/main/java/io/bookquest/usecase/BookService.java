package io.bookquest.usecase;

import io.bookquest.entrypoint.v1.dto.BookEntrypoint;
import io.bookquest.entrypoint.v1.dto.ReadingEntrypoint;
import io.bookquest.entrypoint.v1.integration.database.dto.BookDataTransfer;
import io.bookquest.entrypoint.v1.integration.database.dto.ReadingRecord;
import io.bookquest.entrypoint.v1.integration.database.dto.RecordDataTransfer;
import io.bookquest.entrypoint.v1.integration.database.dto.UserDataTransfer;
import io.bookquest.entrypoint.v1.integration.openlibrary.OpenLibraryClient;
import io.bookquest.entrypoint.v1.integration.openlibrary.dto.BookOpenLibrary;
import io.bookquest.entrypoint.v1.integration.openlibrary.dto.EnvelopeData;
import io.bookquest.entrypoint.v1.mapper.BookMapper;
import io.bookquest.persistence.repository.BookRepository;
import io.bookquest.usecase.categories.CategoriesEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static java.util.Objects.nonNull;
import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.CompletableFuture.runAsync;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;


@Service
public class BookService {

    @Autowired
    private OpenLibraryClient openLibraryClient;

    @Autowired
    private BookRepository repository;

    @Autowired
    private DatabaseRepository databaseRepository;

    public BookEntrypoint processBook(String isbn, String title) {
        if (nonNull(title)) {
            return searchWithTitleAndCreate(title);
        } else if (nonNull(isbn)) {
            return createBook(isbn);
        }

        throw new ResponseStatusException(BAD_REQUEST, "necessário ter pelo menos um parâmetro na requisição: [title, isbn]");
    }

    public void saveBookToUserInventory(String username, String isbn, ReadingEntrypoint reading) {
        ReadingRecord readingGet = databaseRepository.getReading(username, isbn)
                .stream().findFirst()
                .orElseThrow();

        int pagesRead = readingGet.pagesRead() != null ? readingGet.pagesRead() : 0;
        int xp = Math.abs(pagesRead - reading.pagesRead());
        int xpGained = pagesRead + xp;
        databaseRepository.saveReading(username, isbn, reading);
        var userUpdateXP = new UserDataTransfer(null, null, null, null,  xpGained, null, null);

        databaseRepository.saveCreate(username, userUpdateXP);
    }

    private BookEntrypoint searchWithTitleAndCreate(String title) {
        var bookApex = databaseRepository.getBook(title, null, null)
                .stream().findFirst();

        if (bookApex.isPresent())
            return BookMapper.toDto(bookApex.get());

        EnvelopeData envelopeData = openLibraryClient.searchBookByParam(title);
        BookOpenLibrary book = envelopeData.getDocs().stream()
                .filter(doc -> doc.getTitle().equalsIgnoreCase(title) && doc.getPagesMedian() != null)
                .findFirst().orElseThrow();
        book.setSearch(true);
        return populateDataAndSave(book, envelopeData);
    }

    private BookEntrypoint createBook(String isbn) {
        Optional<BookDataTransfer> bookApex = Optional.empty();
        if (isbn.length() == 13)
            bookApex = databaseRepository.getBook(null, null, isbn)
                .stream().findFirst();
        else
            bookApex = databaseRepository.getBook(null, isbn, null)
                    .stream().findFirst();

        if (bookApex.isPresent())
            return BookMapper.toDto(bookApex.get());

        BookOpenLibrary book = openLibraryClient.getBookByISBN(isbn);
        String titleName = book.getTitle();
        EnvelopeData envelopeData = openLibraryClient.searchBookByParam(titleName);
        book.setSearch(false);
        return populateDataAndSave(book, envelopeData);
    }

    private BookEntrypoint populateDataAndSave(BookOpenLibrary book, EnvelopeData envelopeData) {
        var categories = new ArrayList<String>();
        envelopeData.getDocs().stream()
                .map(doc -> getCategories(doc.getSubject()))
                .forEach(categories::addAll);
        book.setCategories(categories);
        return saveBook(book);
    }

    public BookEntrypoint saveBook(BookOpenLibrary book) {
        BookDataTransfer bookDataTransfer = BookMapper.toEntity(book);
        var asyncSaveBook = runAsync(() -> databaseRepository.saveBook(bookDataTransfer));
        List<String> categories = book.getCategories().stream()
                .distinct().toList();
        var asyncSaveCategory = runAsync(() -> databaseRepository.saveCategories(categories));

        allOf(asyncSaveBook, asyncSaveCategory)
                .join();

        var bookCategoryRelation = categories.stream().map(
                category -> BookMapper.toBookCategory(bookDataTransfer, category)
        ).toList();

        var recordDataTransfer = new RecordDataTransfer(true, bookCategoryRelation);
        databaseRepository.saveBookCategory(recordDataTransfer);

        return BookMapper.toDto(bookDataTransfer, categories);
    }

    private boolean containsSubject(List<String> subjects) {
        return subjects != null && !subjects.isEmpty();
    }

    private List<String> getCategories(List<String> subjects) {
        return Arrays.stream(CategoriesEnum.values())
                .filter(category -> containsSubject(subjects) && subjects.contains(category.getCategory()))
                .map(CategoriesEnum::getTranslateCategory)
                .toList();
    }

    public void getBooksFromUser(String idUser, String pageSize, String page) {
        List<ReadingRecord> reading = databaseRepository.getBookFromUser(idUser, pageSize, page);
    }
}


