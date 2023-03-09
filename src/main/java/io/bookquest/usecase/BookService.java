package io.bookquest.usecase;

import io.bookquest.entrypoint.v1.dto.BookEntrypoint;
import io.bookquest.entrypoint.v1.dto.ReadingEntrypoint;
import io.bookquest.entrypoint.v1.integration.database.dto.BookDataTransfer;
import io.bookquest.entrypoint.v1.integration.database.dto.RecordDataTransfer;
import io.bookquest.entrypoint.v1.integration.openlibrary.OpenLibraryClient;
import io.bookquest.entrypoint.v1.integration.openlibrary.dto.BookOpenLibrary;
import io.bookquest.entrypoint.v1.integration.openlibrary.dto.EnvelopeData;
import io.bookquest.entrypoint.v1.mapper.BookMapper;
import io.bookquest.persistence.repository.BookRepository;
import io.bookquest.usecase.categories.CategoriesEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.Objects.nonNull;
import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.CompletableFuture.runAsync;
import static org.springframework.http.HttpStatus.BAD_REQUEST;


@Service
public class BookService {

    @Autowired
    private OpenLibraryClient openLibraryClient;

    @Autowired
    private BookRepository repository;

    @Autowired
    private DatabaseService databaseService;

    public BookEntrypoint processBook(String isbn, String title) {
        if (nonNull(title)) {
            return searchWithTitleAndCreate(title);
        } else if (nonNull(isbn)) {
            return createBook(isbn);
        }

        throw new ResponseStatusException(BAD_REQUEST, "necessário ter pelo menos um parâmetro na requisição: [title, isbn]");
    }

    public void saveBookToUserInventory(String username, String isbn, ReadingEntrypoint reading) {
        databaseService.saveReading(username, isbn, reading);
    }

    private BookEntrypoint searchWithTitleAndCreate(String title) {
        EnvelopeData envelopeData = openLibraryClient.searchBookByParam(title);
        BookOpenLibrary book = envelopeData.getDocs().stream()
                .filter(doc -> doc.getTitle().equalsIgnoreCase(title) && doc.getPagesMedian() != null)
                .findFirst().orElseThrow();
        book.setSearch(true);
        return populateDataAndSave(book, envelopeData);
    }

    private BookEntrypoint createBook(String isbn) {
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
        var asyncSaveBook = runAsync(() -> databaseService.saveBook(bookDataTransfer));
        var asyncSaveCategory = runAsync(() -> databaseService.saveCategories(book.getCategories()));

        allOf(asyncSaveBook, asyncSaveCategory)
                .join();

        var bookCategoryRelation = book.getCategories().stream().map(
                category -> BookMapper.toBookCategory(bookDataTransfer, category)
        ).toList();

        var recordDataTransfer = new RecordDataTransfer(true, bookCategoryRelation);
        databaseService.saveBookCategory(recordDataTransfer);

        return BookMapper.toDto(bookDataTransfer, book.getCategories());
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
}


