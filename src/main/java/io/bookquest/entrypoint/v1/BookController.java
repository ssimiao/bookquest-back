package io.bookquest.entrypoint.v1;

import io.bookquest.entrypoint.v1.dto.BookEntrypoint;
import io.bookquest.entrypoint.v1.dto.ReadingEntrypoint;
import io.bookquest.usecase.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class BookController {

    @Autowired
    private BookService bookService;

    @GetMapping("/books")
    public BookEntrypoint getBook(@RequestParam(value = "isbn", required = false) String isbn,
                                  @RequestParam(value = "title", required = false) String bookTitle) {

        return bookService.processBook(isbn, bookTitle);
    }

    @GetMapping("/users/{idUser}/books")
    public void getAllBookFromUser(@PathVariable("idUser") String idUser,
                                   @RequestParam(name = "page_size", required = false, defaultValue = "200") String pageSize,
                                   @RequestParam(name = "page", required = false, defaultValue = "200") String page) {
        bookService.getBooksFromUser(idUser, pageSize, page);
    }

    @PatchMapping("/users/{username}/books/{isbn}")
    public ResponseEntity<Void> saveBookToUser(@PathVariable("username") String username,
                                                 @PathVariable("isbn") String isbn,
                                                 @RequestBody ReadingEntrypoint reading) {
        bookService.saveBookToUserInventory(username, isbn, reading);
        return ResponseEntity.noContent().build();
    }
}
