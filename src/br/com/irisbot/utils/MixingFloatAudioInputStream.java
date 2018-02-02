package br.com.irisbot.utils;
/*
 * Copyright (c) 2002-2014, Mairie de Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */

import org.tritonus.share.sampled.FloatSampleBuffer;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;


/**
 * Mixing of multiple AudioInputStreams to one AudioInputStream. This class
 * takes a collection of AudioInputStreams and mixes them together. Being a
 * subclass of AudioInputStream itself, reading from instances of this class
 * behaves as if the mixdown result of the input streams is read.
 * <p>
 * This class uses the FloatSampleBuffer for easy conversion using normalized
 * samples in the buffers.
 */
public class MixingFloatAudioInputStream extends AudioInputStream
{
    private AudioInputStream[] _audioInputStreamArray;

    /**
     * Attenuate the stream by how many dB per mixed stream. For example, if
     * attenuationPerStream is 2dB, and 3 streams are mixed together, the mixed
     * stream will be attenuated by 6dB. Set to 0 to not attenuate the signal,
     * which will usually give good results if only 2 streams are mixed
     * together.
     */
    private float _attenuationPerStream = 0.1f;

    /**
     * The linear factor to apply to all samples (derived from
     * attenuationPerStream). This is a factor in the range of 0..1 (depending
     * on attenuationPerStream and the number of streams).
     */
    private float _attenuationFactor = 1.0f;
    private float _parametredAttenuationFactor;
    private FloatSampleBuffer _mixBuffer;
    private FloatSampleBuffer _readBuffer;

    /**
     * A buffer for byte to float conversion.
     */
    private byte[] _tempBuffer;

    /**
     *
     * @param audioFormat the audio Format
     * @param original the original
     * @param background the background
     * @param backgroundAttenuationFactor the backgroundAttenuationFactor
     */
    public MixingFloatAudioInputStream( AudioFormat audioFormat, AudioInputStream original,
        AudioInputStream background, float backgroundAttenuationFactor )
    {
        super( new ByteArrayInputStream( new byte[0] ), audioFormat, AudioSystem.NOT_SPECIFIED );

        _audioInputStreamArray = new AudioInputStream[2];
        _audioInputStreamArray[0] = original;
        _audioInputStreamArray[1] = background;

        // set up the static mix buffer with initially no samples. Note that
        // using a static mix buffer prevents that this class can be used at
        // once from different threads, but that wouldn't be useful anyway. But
        // by re-using this buffer we save a lot of garbage collection.
        _mixBuffer = new FloatSampleBuffer( audioFormat.getChannels(  ), 0, audioFormat.getSampleRate(  ) );

        // ditto for the read buffer. It is used for reading samples from the
        // underlying streams.
        _readBuffer = new FloatSampleBuffer(  );

        // calculate the linear attenuation factor
        _attenuationFactor = decibel2linear( -1.0f * _attenuationPerStream * 2 );

        if ( backgroundAttenuationFactor > _attenuationFactor )
        {
            _parametredAttenuationFactor = _attenuationFactor;
        }
        else
        {
            _parametredAttenuationFactor = backgroundAttenuationFactor;
        }
    }

    /**
     * The maximum of the frame length of the input stream is calculated and
     * returned. If at least one of the input streams has length
     * <code>AudioInputStream.NOT_SPECIFIED</code>, this value is returned.
     *
     * @return the frame length
     */
    public long getFrameLength(  )
    {
        long lLengthInFrames = 0;

        for ( AudioInputStream stream : _audioInputStreamArray )
        {
            long lLength = stream.getFrameLength(  );

            if ( lLength == AudioSystem.NOT_SPECIFIED )
            {
                return AudioSystem.NOT_SPECIFIED;
            }
            else
            {
                lLengthInFrames = Math.max( lLengthInFrames, lLength );
            }
        }

        return lLengthInFrames;
    }

    /**
     * @return the byte read
     * @throws IOException the IOException
     */
    public int read(  ) throws IOException
    {
        byte[] samples = new byte[1];
        int ret = read( samples );

        if ( ret != 1 )
        {
            return -1;
        }

        return samples[0];
    }

    /**
     * @param abData the Data
     * @param nOffset the Offset
     * @param nLength the Length
     * @return the byte read
     * @throws IOException the IOException
     *
     */
    public int read( byte[] abData, int nOffset, int nLength )
        throws IOException
    {
        // set up the mix buffer with the requested size
        _mixBuffer.changeSampleCount( nLength / getFormat(  ).getFrameSize(  ), false );

        // initialize the mixBuffer with silence
        _mixBuffer.makeSilence(  );

        // remember the maximum number of samples actually mixed
        int maxMixed = 0;

        for ( int streamNumber = 0; streamNumber < _audioInputStreamArray.length; streamNumber++ )
        {
            // calculate how many bytes we need to read from this stream
            int needRead = _mixBuffer.getSampleCount(  ) * _audioInputStreamArray[streamNumber].getFormat(  )
                                                                                               .getFrameSize(  );

            // set up the temporary byte buffer
            if ( ( _tempBuffer == null ) || ( _tempBuffer.length < needRead ) )
            {
                _tempBuffer = new byte[needRead];
            }

            // read from the source stream
            int bytesRead = _audioInputStreamArray[streamNumber].read( _tempBuffer, 0, needRead );

            if ( bytesRead == -1 )
            {
                // end of stream: remove it from the list of streams.
                continue;
            }

            // now convert this buffer to float samples
            _readBuffer.initFromByteArray( _tempBuffer, 0, bytesRead, _audioInputStreamArray[streamNumber].getFormat(  ) );

            if ( maxMixed < _readBuffer.getSampleCount(  ) )
            {
                maxMixed = _readBuffer.getSampleCount(  );
            }

            // the actual mixing routine: add readBuffer to mixBuffer
            // can only mix together as many channels as available
            int maxChannels = Math.min( _mixBuffer.getChannelCount(  ), _readBuffer.getChannelCount(  ) );

            for ( int channel = 0; channel < maxChannels; channel++ )
            {
                // get the arrays of the normalized float samples
                float[] readSamples = _readBuffer.getChannel( channel );
                float[] mixSamples = _mixBuffer.getChannel( channel );

                // Never use readSamples.length or mixSamples.length: the length
                // of the array may be longer than the actual buffer ("lazy"
                // deletion).
                int maxSamples = Math.min( _mixBuffer.getSampleCount(  ), _readBuffer.getSampleCount(  ) );

                // in a loop, add each "read" sample to the mix buffer
                // can only mix as many samples as available. Also apply the
                // attenuation factor.

                // Note1: the attenuation factor could also be applied only once
                // in a separate loop after mixing all the streams together,
                // saving processor time in case of many mixed streams.

                // Note2: adding everything together here will not cause
                // clipping, because all samples are in float format.
                if ( streamNumber == 1 )
                {
                    for ( int sample = 0; sample < maxSamples; sample++ )
                    {
                        mixSamples[sample] += ( _parametredAttenuationFactor * readSamples[sample] );
                    }
                }
                else
                {
                    for ( int sample = 0; sample < maxSamples; sample++ )
                    {
                        mixSamples[sample] += ( _attenuationFactor * readSamples[sample] );
                    }
                }
            }
        }

        if ( maxMixed == 0 )
        {
            // nothing written to the mixBuffer
            if ( _audioInputStreamArray.length == 0 )
            {
                // nothing mixed, no more streams available: end of stream
                return -1;
            }

            // nothing written, but still streams to read from
            return 0;
        }

        // finally convert the mix Buffer to the requested byte array.
        // This routine will handle clipping, i.e. if there are samples > 1.0f
        // in the mix buffer, they will be clipped to 1.0f and converted to the
        // specified audioFormat's sample format.
       // _mixBuffer.convertToByteArray( 0, maxMixed, abData, nOffset, getFormat(  ) );
        _mixBuffer.convertToByteArray( abData, nOffset, getFormat(  ) );

        return maxMixed * getFormat(  ).getFrameSize(  );
    }

    /**
     * calls skip() on all input streams. There is no way to assure that the
     * number of bytes really skipped is the same for all input streams. Due to
     * that, this method always returns the passed value. In other words: the
     * return value is useless (better ideas appreciated).
     *
     * @param lLength the length
     * @return the length
     * @throws IOException the IOException
     */
    public long skip( long lLength ) throws IOException
    {
        for ( AudioInputStream stream : _audioInputStreamArray )
        {
            stream.skip( lLength );
        }

        return lLength;
    }

    /**
     * The minimum of available() of all input stream is calculated and
     * returned.
     *
     * @return the available
     * @throws IOException the IOException
     */
    public int available(  ) throws IOException
    {
        int nAvailable = 0;

        for ( AudioInputStream stream : _audioInputStreamArray )
        {
            nAvailable = Math.min( nAvailable, stream.available(  ) );
        }

        return nAvailable;
    }

    /**
     * @throws IOException the IOException
     */
    public void close(  ) throws IOException
    {
        // TODO: should we close all streams in the list?
    }

    /**
     * Calls mark() on all input streams.
     *
     * @param nReadLimit the read limit
     */
    public void mark( int nReadLimit )
    {
        for ( AudioInputStream stream : _audioInputStreamArray )
        {
            stream.mark( nReadLimit );
        }
    }

    /**
     * Calls reset() on all input streams.
     *
     * @throws IOException the IOException
     */
    public void reset(  ) throws IOException
    {
        for ( AudioInputStream stream : _audioInputStreamArray )
        {
            stream.reset(  );
        }
    }

    /**
     * returns true if all input stream return true for markSupported().
     *
     * @return false if mark supported
     */
    public boolean markSupported(  )
    {
        for ( AudioInputStream stream : _audioInputStreamArray )
        {
            if ( !stream.markSupported(  ) )
            {
                return false;
            }
        }

        return true;
    }

    /**
     *
     * @param decibels the decibels
     * @return the decibel linear
     */
    public static float decibel2linear( float decibels )
    {
        return (float) Math.pow( 10.0, decibels / 20.0 );
    }
}
/** * MixingFloatAudioInputStream.java ** */