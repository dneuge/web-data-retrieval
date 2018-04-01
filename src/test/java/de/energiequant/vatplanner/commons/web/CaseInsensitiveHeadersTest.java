package de.energiequant.vatplanner.commons.web;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.http.Header;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(DataProviderRunner.class)
public class CaseInsensitiveHeadersTest {
    private CaseInsensitiveHeaders spyHeaders;
    
    @Rule
    public ExpectedException thrown = ExpectedException.none();
    
    @Before
    public void setUp() {
        CaseInsensitiveHeaders templateHeaders = new CaseInsensitiveHeaders();
        spyHeaders = spy(templateHeaders);
    }
    
    @Test
    public void testAddAll_null_doesNotAdd() {
        // Arrange (nothing to do)
        
        // Act
        spyHeaders.addAll(null);
        
        // Assert
        verify(spyHeaders, never()).add(Mockito.anyString(), Mockito.anyString());
    }
    
    @Test
    public void testAddAll_emptyArray_doesNotAdd() {
        // Arrange
        Header[] arr = new Header[0];
        
        // Act
        spyHeaders.addAll(arr);
        
        // Assert
        verify(spyHeaders, never()).add(Mockito.anyString(), Mockito.anyString());
    }
    
    @Test
    public void testAddAll_fullArray_onlyAddsAllItems() {
        // Arrange
        Header[] arr = new Header[]{
            mockHeader("a", "0123"),
            mockHeader("B-name", "something; here"),
            mockHeader("A", "3210"),
            mockHeader("B_name", "not the same"),
            mockHeader("b-NAME", "but, this is"),
        };
        
        // Act
        spyHeaders.addAll(arr);
        
        // Assert
        InOrder inOrder = inOrder(spyHeaders);
        inOrder.verify(spyHeaders).add(Mockito.eq("a"), Mockito.eq("0123"));
        inOrder.verify(spyHeaders).add(Mockito.eq("B-name"), Mockito.eq("something; here"));
        inOrder.verify(spyHeaders).add(Mockito.eq("A"), Mockito.eq("3210"));
        inOrder.verify(spyHeaders).add(Mockito.eq("B_name"), Mockito.eq("not the same"));
        inOrder.verify(spyHeaders).add(Mockito.eq("b-NAME"), Mockito.eq("but, this is"));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testGetAll_nothingAdded_returnsEmptyMap() {
        // Arrange (nothing to do)
        
        // Act
        Map<String, List<String>> res = spyHeaders.getAll();
        
        // Assert
        assertThat(res.entrySet(), is(empty()));
    }

    @Test
    public void testGetAll_addedMultipleValues_returnsAllExpectedItemsIndexedByLowerCaseName() {
        // Arrange
        spyHeaders.add("some key", "my first value");
        spyHeaders.add("Another Name", "123, 456; 789");
        spyHeaders.add("SOME keY", "with a value");
        spyHeaders.add("aNoThEr nAmE", "guess what");
        
        // Act
        Map<String, List<String>> res = spyHeaders.getAll();
        
        // Assert
        assertThat(res.size(), is(2));
        assertThat(res, hasEntry(equalTo("some key"), contains("my first value", "with a value")));
        assertThat(res, hasEntry(equalTo("another name"), contains("123, 456; 789", "guess what")));
    }

    @Test
    public void testGetAll_addedValue_mapIsUnmodifiable() {
        // Arrange
        spyHeaders.add("some key", "my first value");
        
        // Act
        Map<String, List<String>> res = spyHeaders.getAll();

        thrown.expect(UnsupportedOperationException.class);

        res.put("Can I break it?", new LinkedList<>());
        
        // Assert (nothing to do)
    }

    @Test
    public void testGetAll_addedValue_listIsUnmodifiable() {
        // Arrange
        spyHeaders.add("some key", "my first value");
        
        // Act
        Map<String, List<String>> res = spyHeaders.getAll();
        List<String> list = res.get("some key");
        
        thrown.expect(UnsupportedOperationException.class);
        
        list.add("Can I break it?");
        
        // Assert (nothing to do)
    }

    @Test
    public void testGetAllByName_addedMultipleValuesForName_returnsAllExpectedItems() {
        // Arrange
        spyHeaders.add("not related", "123");
        spyHeaders.add("this alternates", "first entry");
        spyHeaders.add("This ALTERNATES", "That's the second entry.");
        spyHeaders.add("something else", "should not be returned");
        spyHeaders.add("THIS alternates", "will; include, all !$§(/-% special chars as well");
        spyHeaders.add("Finishing-This-List", "... and again not expected!");
        
        // Act
        List<String> res = spyHeaders.getAllByName("tHiS aLtErNaTeS");
        
        // Assert
        assertThat(res, contains("first entry", "That's the second entry.", "will; include, all !$§(/-% special chars as well"));
    }

    @Test
    public void testGetAllByName_nameWithoutValues_returnsEmptyList() {
        // Arrange
        spyHeaders.add("not related", "123");
        
        // Act
        List<String> res = spyHeaders.getAllByName("missing");
        
        // Assert
        assertThat(res, is(empty()));
    }

    @Test
    public void testGetAllByName_nameWithValue_listIsUnmodifiable() {
        // Arrange
        spyHeaders.add("wanted", "content");
        
        thrown.expect(UnsupportedOperationException.class);
        
        // Act
        List<String> res = spyHeaders.getAllByName("wanted");
        res.add("duh");
        
        // Assert (nothing to do)
    }
    
    @Test
    @DataProvider({"1st", "first element"})
    public void testGetFirstByName_getAllByNameReturnsListWithElements_returnsFirstEntryOfList(String expectedString) {
        // Arrange
        List<String> list = Arrays.asList(expectedString, "second", "third");
        doReturn(list).when(spyHeaders).getAllByName(Mockito.anyString());
        
        // Act
        String res = spyHeaders.getFirstByName("does not matter");
        
        // Assert
        assertThat(res, is(equalTo(expectedString)));
    }
    
    @Test
    public void testGetFirstByName_getAllByNameReturnsEmptyList_returnsNull() {
        // Arrange
        List<String> list = new ArrayList<>();
        doReturn(list).when(spyHeaders).getAllByName(Mockito.anyString());
        
        // Act
        String res = spyHeaders.getFirstByName("does not matter");
        
        // Assert
        assertThat(res, is(nullValue()));
    }
    
    private Header mockHeader(String name, String value) {
        Header mock = mock(Header.class);
        when(mock.getName()).thenReturn(name);
        when(mock.getValue()).thenReturn(value);
        
        return mock;
    }
}
