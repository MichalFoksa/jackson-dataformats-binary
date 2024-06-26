package tools.jackson.dataformat.protobuf;

import tools.jackson.core.ErrorReportConfiguration;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.base.DecorableTSFactory.DecorableTSFBuilder;

/**
 * {@link tools.jackson.core.TSFBuilder}
 * implementation for constructing {@link ProtobufFactory}
 * instances.
 *
 * @since 3.0
 */
public class ProtobufFactoryBuilder extends DecorableTSFBuilder<ProtobufFactory, ProtobufFactoryBuilder>
{
    public ProtobufFactoryBuilder() {
        super(StreamReadConstraints.defaults(), StreamWriteConstraints.defaults(),
                ErrorReportConfiguration.defaults(),
                0, 0);
    }

    public ProtobufFactoryBuilder(ProtobufFactory base) {
        super(base);
    }

    @Override
    public ProtobufFactory build() {
        // 28-Dec-2017, tatu: No special settings beyond base class ones, so:
        return new ProtobufFactory(this);
    }
}
