package tools.jackson.dataformat.avro.deser;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;

import tools.jackson.core.*;
import tools.jackson.core.io.IOContext;
import tools.jackson.core.sym.PropertyNameMatcher;
import tools.jackson.dataformat.avro.AvroParser;
import tools.jackson.dataformat.avro.AvroSchema;

/**
 * Implementation base class that exposes additional internal API
 * to be used as callbacks by {@link AvroReadContext} implementations.
 */
public abstract class AvroParserImpl
    extends AvroParser
{
    /*
    /**********************************************************************
    /* Decoding state
    /**********************************************************************
     */

    /**
     * Index of the union branch that was followed to reach the current token. This is cleared when the next token is read.
     */
    protected int _branchIndex;

    /**
     * Index of the enum that was read as the current token. This is cleared when the next token is read.
     */
    protected int _enumIndex;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    protected AvroParserImpl(ObjectReadContext readCtxt, IOContext ioCtxt,
            int parserFeatures, int avroFeatures,
            AvroSchema schema)
    {
        super(readCtxt, ioCtxt, parserFeatures, avroFeatures);
        setSchema(schema);
    }

    @Override
    public void close() {
        // 20-Apr-2017, tatu: Let's simplify some checks by changing context
        _avroContext = MissingReader.closedInstance;
        super.close();
    }

    /*
    /**********************************************************************
    /* Abstract method impls, traversal, basic
    /**********************************************************************
     */

    @Override
    public JsonToken nextToken() throws JacksonException
    {
        // note: closed-ness check by context, not needed here
        _numTypesValid = NR_UNKNOWN;
        _tokenInputTotal = _currInputProcessed + _inputPtr;
        _branchIndex = -1;
        _enumIndex = -1;
        _binaryValue = null;
        JsonToken t;
        try {
            t = _avroContext.nextToken();
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        _currToken = t;
        return t;
    }

    /**
     * Skip to the end of the current structure (array/map/object); This is different
     * from {@link #skipMap()} and {@link #skipArray()} because it operates at the parser
     * level instead of at the decoder level and advances the parsing context in addition
     * to consuming the data from the input.
     *
     * @throws JacksonException If there was an issue advancing through the underlying data stream
     */
    public final void skipValue() throws JacksonException {
        try {
            _avroContext.skipValue(this);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }
    
    /*
    /**********************************************************************
    /* Abstract method impls, traversal, names
    /**********************************************************************
     */

    @Override
    public String nextName() throws JacksonException
    {
        // note: closed-ness check by context, not needed here
        _numTypesValid = NR_UNKNOWN;
        _tokenInputTotal = _currInputProcessed + _inputPtr;
        _binaryValue = null;
        String name;
        try {
            name = _avroContext.nextName();
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        if (name == null) {
            _currToken = _avroContext.currentToken();
            return null;
        }
        _currToken = JsonToken.PROPERTY_NAME;
        return name;
    }

    @Override
    public boolean nextName(SerializableString sstr) throws JacksonException
    {
        // note: closed-ness check by context, not needed here
        _numTypesValid = NR_UNKNOWN;
        _tokenInputTotal = _currInputProcessed + _inputPtr;
        _binaryValue = null;
        String name;
        try {
            name = _avroContext.nextName();
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        if (name == null) {
            _currToken = _avroContext.currentToken();
            return false;
        }
        _currToken = JsonToken.PROPERTY_NAME;
        String toMatch = sstr.getValue();
        if (toMatch == name) {
            return true;
        }
        return toMatch.equals(name);
    }

    @Override
    public int nextNameMatch(PropertyNameMatcher matcher) throws JacksonException
    {
        // note: closed-ness check by context, not needed here
        _numTypesValid = NR_UNKNOWN;
        _tokenInputTotal = _currInputProcessed + _inputPtr;
        _binaryValue = null;

        int match;
        try {
            match = _avroContext.nextNameMatch(matcher);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        // 20-Dec-2017, tatu: not sure check would be any faster
        _currToken = _avroContext.currentToken();
/*
        if (match < 0) { // END_OBJECT, mismatching PROPERTY_NAME or something else:
            _currToken = _avroContext.currentToken();
        } else {
            _currToken = JsonToken.PROPERTY_NAME;
        }
*/
        return match;
    }

    /*
    /**********************************************************************
    /* Abstract method impls, traversal, values
    /**********************************************************************
     */

    @Override
    public abstract String nextTextValue() throws JacksonException;

    @Override
    public final void _initSchema(AvroSchema schema) throws JacksonException {
        _avroContext = new RootReader(this, schema.getReader());
    }

    /*
    /**********************************************************************
    /* Numeric accessors of public API
    /**********************************************************************
     */

    @Override
    public final boolean isNaN() {
        if (_currToken == JsonToken.VALUE_NUMBER_FLOAT) {
            if ((_numTypesValid & NR_DOUBLE) != 0) {
                return !Double.isFinite(_numberDouble);
            }
            if ((_numTypesValid & NR_FLOAT) != 0) {
                return !Float.isFinite(_numberFloat);
            }
        }
        return false;
    }

    @Override
    public final Number getNumberValue() throws JacksonException
    {
        if (_numTypesValid == NR_UNKNOWN) {
            _checkNumericValue(NR_UNKNOWN); // will also check event type
        }
        // Separate types for int types
        if (_currToken == JsonToken.VALUE_NUMBER_INT) {
            if ((_numTypesValid & NR_INT) != 0) {
                return _numberInt;
            }
            if ((_numTypesValid & NR_LONG) != 0) {
                return _numberLong;
            }
            if ((_numTypesValid & NR_BIGINT) != 0) {
                return _numberBigInt;
            }
            // Shouldn't get this far but if we do
            return _numberBigDecimal;
        }

        // And then floating point types. But here optimal type
        // needs to be big decimal, to avoid losing any data?
        if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            return _numberBigDecimal;
        }
        if ((_numTypesValid & NR_DOUBLE) != 0) {
            return _numberDouble;
        }
        if ((_numTypesValid & NR_FLOAT) == 0) { // sanity check
            _throwInternal();
        }
        return _numberFloat;
    }

    @Override // @since 2.12 -- for (most?) binary formats exactness guaranteed anyway
    public final Number getNumberValueExact() throws JacksonException {
        return getNumberValue();
    }

    @Override
    public final NumberType getNumberType() throws JacksonException
    {
        if (_numTypesValid == NR_UNKNOWN) {
            _checkNumericValue(NR_UNKNOWN); // will also check event type
        }
        if (_currToken == JsonToken.VALUE_NUMBER_INT) {
            if ((_numTypesValid & NR_INT) != 0) {
                return NumberType.INT;
            }
            if ((_numTypesValid & NR_LONG) != 0) {
                return NumberType.LONG;
            }
            return NumberType.BIG_INTEGER;
        }

        // And then floating point types. Here optimal type should be big decimal,
        // to avoid losing any data? However... using BD is slow, so let's allow returning
        // double as type if no explicit call has been made to access data as BD?
        if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            return NumberType.BIG_DECIMAL;
        }
        if ((_numTypesValid & NR_DOUBLE) != 0) {
            return NumberType.DOUBLE;
        }
        return NumberType.FLOAT;
    }

    @Override // since 2.17
    public NumberTypeFP getNumberTypeFP() throws JacksonException {
        if (_currToken == JsonToken.VALUE_NUMBER_FLOAT) {
            if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
                return NumberTypeFP.BIG_DECIMAL;
            }
            if ((_numTypesValid & NR_DOUBLE) != 0) {
                return NumberTypeFP.DOUBLE64;
            }
            if ((_numTypesValid & NR_FLOAT) != 0) {
                return NumberTypeFP.FLOAT32;
            }
        }
        return NumberTypeFP.UNKNOWN;
    }

    @Override
    public final float getFloatValue() throws JacksonException
    {
        if ((_numTypesValid & NR_FLOAT) == 0) {
            if (_numTypesValid == NR_UNKNOWN) {
                _checkNumericValue(NR_FLOAT);
            }
            if ((_numTypesValid & NR_FLOAT) == 0) {
                convertNumberToFloat();
            }
        }
        // Bounds/range checks would be tricky here, so let's not bother even trying...
        /*
        if (value < -Float.MAX_VALUE || value > MAX_FLOAT_D) {
            _reportError("Numeric value ("+getText()+") out of range of Java float");
        }
        */
        return _numberFloat;
    }

    /*
    /**********************************************************************
    /* Numeric conversions
    /**********************************************************************
     */

    protected final void _checkNumericValue(int expType) throws JacksonException
    {
        // Int or float?
        if (_currToken == JsonToken.VALUE_NUMBER_INT || _currToken == JsonToken.VALUE_NUMBER_FLOAT) {
            return;
        }
        _reportError("Current token ("+currentToken()+") not numeric, can not use numeric value accessors");
    }

    @Override
    protected final void convertNumberToInt() throws JacksonException
    {
        // First, converting from long ought to be easy
        if ((_numTypesValid & NR_LONG) != 0) {
            // Let's verify it's lossless conversion by simple roundtrip
            int result = (int) _numberLong;
            if (((long) result) != _numberLong) {
                _reportError("Numeric value ("+getText()+") out of range of int");
            }
            _numberInt = result;
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            if (BI_MIN_INT.compareTo(_numberBigInt) > 0
                    || BI_MAX_INT.compareTo(_numberBigInt) < 0) {
                _reportOverflowInt();
            }
            _numberInt = _numberBigInt.intValue();
        } else if ((_numTypesValid & NR_DOUBLE) != 0) {
            // Need to check boundaries
            if (_numberDouble < MIN_INT_D || _numberDouble > MAX_INT_D) {
                _reportOverflowInt();
            }
            _numberInt = (int) _numberDouble;
        } else if ((_numTypesValid & NR_FLOAT) != 0) {
            if (_numberFloat < MIN_INT_D || _numberFloat > MAX_INT_D) {
                _reportOverflowInt();
            }
            _numberInt = (int) _numberFloat;
        } else if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            if (BD_MIN_INT.compareTo(_numberBigDecimal) > 0
                || BD_MAX_INT.compareTo(_numberBigDecimal) < 0) {
                _reportOverflowInt();
            }
            _numberInt = _numberBigDecimal.intValue();
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_INT;
    }

    @Override
    protected final void convertNumberToLong() throws JacksonException
    {
        if ((_numTypesValid & NR_INT) != 0) {
            _numberLong = _numberInt;
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            if (BI_MIN_LONG.compareTo(_numberBigInt) > 0
                    || BI_MAX_LONG.compareTo(_numberBigInt) < 0) {
                _reportOverflowLong();
            }
            _numberLong = _numberBigInt.longValue();
        } else if ((_numTypesValid & NR_DOUBLE) != 0) {
            if (_numberDouble < MIN_LONG_D || _numberDouble > MAX_LONG_D) {
                _reportOverflowLong();
            }
            _numberLong = (long) _numberDouble;
        } else if ((_numTypesValid & NR_FLOAT) != 0) {
            if (_numberFloat < MIN_LONG_D || _numberFloat > MAX_LONG_D) {
                _reportOverflowInt();
            }
            _numberLong = (long) _numberFloat;
        } else if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            if (BD_MIN_LONG.compareTo(_numberBigDecimal) > 0
                || BD_MAX_LONG.compareTo(_numberBigDecimal) < 0) {
                _reportOverflowLong();
            }
            _numberLong = _numberBigDecimal.longValue();
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_LONG;
    }

    @Override
    protected final void convertNumberToBigInteger() throws JacksonException
    {
        if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            // here it'll just get truncated, no exceptions thrown
            streamReadConstraints().validateBigIntegerScale(_numberBigDecimal.scale());
            _numberBigInt = _numberBigDecimal.toBigInteger();
        } else if ((_numTypesValid & NR_LONG) != 0) {
            _numberBigInt = BigInteger.valueOf(_numberLong);
        } else if ((_numTypesValid & NR_INT) != 0) {
            _numberBigInt = BigInteger.valueOf(_numberInt);
        } else if ((_numTypesValid & NR_DOUBLE) != 0) {
            _numberBigInt = BigDecimal.valueOf(_numberDouble).toBigInteger();
        } else if ((_numTypesValid & NR_FLOAT) != 0) {
            _numberBigInt = BigDecimal.valueOf(_numberFloat).toBigInteger();
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_BIGINT;
    }

    @Override
    protected final void convertNumberToFloat() throws JacksonException
    {
        // Note: this MUST start with more accurate representations, since we don't know which
        //  value is the original one (others get generated when requested)
        if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            _numberFloat = _numberBigDecimal.floatValue();
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            _numberFloat = _numberBigInt.floatValue();
        } else if ((_numTypesValid & NR_DOUBLE) != 0) {
            _numberFloat = (float) _numberDouble;
        } else if ((_numTypesValid & NR_LONG) != 0) {
            _numberFloat = (float) _numberLong;
        } else if ((_numTypesValid & NR_INT) != 0) {
            _numberFloat = (float) _numberInt;
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_FLOAT;
    }

    @Override
    protected final void convertNumberToDouble() throws JacksonException
    {
        // Note: this MUST start with more accurate representations, since we don't know which
        //  value is the original one (others get generated when requested)
        if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            _numberDouble = _numberBigDecimal.doubleValue();
        } else if ((_numTypesValid & NR_FLOAT) != 0) {
            _numberDouble = _numberFloat;
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            _numberDouble = _numberBigInt.doubleValue();
        } else if ((_numTypesValid & NR_LONG) != 0) {
            _numberDouble = (double) _numberLong;
        } else if ((_numTypesValid & NR_INT) != 0) {
            _numberDouble = _numberInt;
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_DOUBLE;
    }

    @Override
    protected final void convertNumberToBigDecimal() throws JacksonException
    {
        // Note: this MUST start with more accurate representations, since we don't know which
        //  value is the original one (others get generated when requested)
        if ((_numTypesValid & NR_DOUBLE) != 0) {
            // 05-Apt-2017, tatu: Unlike with textual formats, we never have textual
            //    representation to work with here
            _numberBigDecimal = new BigDecimal(_numberDouble);
        } else if ((_numTypesValid & NR_FLOAT) != 0) {
            _numberBigDecimal = new BigDecimal(_numberFloat);
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            _numberBigDecimal = new BigDecimal(_numberBigInt);
        } else if ((_numTypesValid & NR_LONG) != 0) {
            _numberBigDecimal = BigDecimal.valueOf(_numberLong);
        } else if ((_numTypesValid & NR_INT) != 0) {
            _numberBigDecimal = BigDecimal.valueOf(_numberInt);
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_BIGDECIMAL;
    }

    /*
    /**********************************************************************
    /* Methods for AvroReadContext implementations: state
    /**********************************************************************
     */

    public abstract boolean checkInputEnd() throws IOException;

    /**
     * Returns the remaining number of elements in the current block of a map or array
     */
    public long getRemainingElements()
    {
        return _avroContext.getRemainingElements();
        /*
        // !!! TODO: maybe just add in `
        if ( _avroContext instanceof ArrayReader) {
            return ((ArrayReader) _avroContext).getRemainingElements();
        }
        if (_avroContext instanceof MapReader) {
            return ((MapReader) _avroContext).getRemainingElements();
        }
        return -1;
        */
    }

    /*
    /**********************************************************************
    /* Methods for AvroReadContext implementations: decoding int
    /**********************************************************************
     */

    public abstract JsonToken decodeIntToken() throws IOException;

    public abstract int decodeInt() throws IOException;

    public abstract void skipInt() throws IOException;

    /*
    /**********************************************************************
    /* Methods for AvroReadContext implementations: decoding long
    /**********************************************************************
     */

    public abstract JsonToken decodeLongToken() throws IOException;

    public abstract long decodeLong() throws IOException;

    public abstract void skipLong() throws IOException;

    /*
    /**********************************************************************
    /* Methods for AvroReadContext implementations: decoding float/double
    /**********************************************************************
     */

    public abstract JsonToken decodeFloat() throws IOException;

    public abstract void skipFloat() throws IOException;

    public abstract JsonToken decodeDouble() throws IOException;

    public abstract void skipDouble() throws IOException;

    /*
    /**********************************************************************
    /* Methods for AvroReadContext implementations: decoding Strings
    /**********************************************************************
     */

    public abstract JsonToken decodeStringToken() throws IOException;

    public abstract void decodeString() throws IOException;

    public abstract void skipString() throws IOException;

    /*
    /**********************************************************************
    /* Methods for AvroReadContext implementations: decoding Bytes
    /**********************************************************************
     */

    public abstract JsonToken decodeBytes() throws IOException;

    public abstract void skipBytes() throws IOException;

    public abstract JsonToken decodeFixed(int size) throws IOException;

    public abstract void skipFixed(int size) throws IOException;

    /*
    /**********************************************************************
    /* Methods for AvroReadContext implementations: decoding Arrays
    /**********************************************************************
     */

    public abstract long decodeArrayStart() throws IOException;

    public abstract long decodeArrayNext() throws IOException;

    /**
     * @return Either 0, in which case all entries have been successfully skipped; or
     *    positive non-zero number to indicate elements that caller has to skip
     */
    public abstract long skipArray() throws IOException;

    /*
    /**********************************************************************
    /* Methods for AvroReadContext implementations: decoding Maps
    /**********************************************************************
     */

    public abstract String decodeMapKey() throws IOException;
    public abstract long decodeMapStart() throws IOException;
    public abstract long decodeMapNext() throws IOException;

    /**
     * @return Either 0, in which case all entries have been successfully skipped; or
     *    positive non-zero number to indicate map entries that caller has to skip
     */
    public abstract long skipMap() throws IOException;

    /*
    /**********************************************************************
    /* Methods for AvroReadContext implementations: misc
    /**********************************************************************
     */

    public abstract JsonToken decodeBoolean() throws IOException;
    public abstract void skipBoolean() throws IOException;
    public abstract int decodeIndex() throws IOException;
    public abstract int decodeEnum() throws IOException;

    /*
    /**********************************************************************
    /* Methods for AvroReadContext impls, other
    /**********************************************************************
     */

    public final int branchIndex() {
        return _branchIndex;
    }

    public final int enumIndex() {
        return _enumIndex;
    }

    public final boolean isRecord() {
        return _avroContext instanceof RecordReader;
    }

    public final void setAvroContext(AvroReadContext ctxt) {
        _avroContext = ctxt;
    }

    /*
    /**********************************************************************
    /* Low-level methods: setting values from defaults
    /**********************************************************************
     */

    protected final JsonToken setBytes(byte[] b)
    {
        _binaryValue = b;
        return JsonToken.VALUE_EMBEDDED_OBJECT;
    }

    protected final JsonToken setNumber(int v) {
        _numberInt = v;
        _numTypesValid = NR_INT;
        return JsonToken.VALUE_NUMBER_INT;
    }

    protected final JsonToken setNumber(long v) {
        _numberLong = v;
        _numTypesValid = NR_LONG;
        return JsonToken.VALUE_NUMBER_INT;
    }

    protected final JsonToken setNumber(float v) {
        _numberFloat = v;
        _numTypesValid = NR_FLOAT;
        return JsonToken.VALUE_NUMBER_FLOAT;
    }

    protected final  JsonToken setNumber(double v) {
        _numberDouble = v;
        _numTypesValid = NR_DOUBLE;
        return JsonToken.VALUE_NUMBER_FLOAT;
    }

    protected abstract JsonToken setString(String str) throws IOException;
}
