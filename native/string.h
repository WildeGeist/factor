typedef struct {
	CELL header;
	/* untagged */
	CELL capacity;
	/* untagged */
	CELL hashcode;
} STRING;

INLINE STRING* untag_string(CELL tagged)
{
	type_check(STRING_TYPE,tagged);
	return (STRING*)UNTAG(tagged);
}

STRING* allot_string(CELL capacity);
STRING* string(CELL capacity, CELL fill);
void hash_string(STRING* str);
STRING* grow_string(STRING* string, CELL capacity, CHAR fill);
char* to_c_string(STRING* s);
STRING* from_c_string(char* c_string);

#define SREF(string,index) ((CELL)string + sizeof(STRING) + index * CHARS)

#define SSIZE(pointer) align8(sizeof(STRING) + \
	((STRING*)pointer)->capacity * CHARS)

/* untagged & unchecked */
INLINE CELL string_nth(STRING* string, CELL index)
{
	return cget(SREF(string,index));
}

/* untagged & unchecked */
INLINE void set_string_nth(STRING* string, CELL index, CHAR value)
{
	cput(SREF(string,index),value);
}

void primitive_stringp(void);
void primitive_string_length(void);
void primitive_string_nth(void);
FIXNUM string_compare(STRING* s1, STRING* s2);
void primitive_string_compare(void);
void primitive_string_eq(void);
void primitive_string_hashcode(void);
void primitive_index_of(void);
void primitive_substring(void);
