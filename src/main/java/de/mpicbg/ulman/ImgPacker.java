/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman;

import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imglib2.img.Img;
import net.imglib2.img.WrappedImg;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
//import net.imglib2.img.array.ArrayImgs; -- see the hing in packAndSendArrayImg()
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess;
import net.imglib2.img.cell.CellImg;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.img.planar.PlanarImg;
import net.imglib2.img.planar.PlanarImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;

import java.util.StringTokenizer;
import java.nio.ByteBuffer;
import org.zeromq.ZMQ;

public class ImgPacker<T extends NativeType<T>>
{
	/**
	 *
	 */
	@SuppressWarnings("unchecked")
	public
	void packAndSend(final ImgPlus<T> imgP, final ZMQ.Socket socket) throws Exception
	{
		//"buffer" for the first and human-readable payload:
		//protocol version
		String msg = new String("v1");

		//dimensionality data
		msg += " dimNumber " + imgP.numDimensions();
		for (int i=0; i < imgP.numDimensions(); ++i)
			msg += " " + imgP.dimension(i);

		//decipher the voxel type
		msg += " " + typeIDToString(typeToTypeID(imgP.firstElement()));

		//check we can handle the storage model of this image,
		//and try to send everything (first the human readable payload, then raw voxel data)
		Img<T> img = getUnderlyingImg(imgP);
		if (img instanceof ArrayImg)
		{
			msg += " ArrayImg ";
			socket.send(msg.getBytes(), ZMQ.SNDMORE);

			//send metadata and voxel data afterwards
			packAndSendPlusData(imgP, socket);
			packAndSendArrayImg((ArrayImg<T, ? extends ArrayDataAccess<?>>)img, socket);
		}
		else
		if (img instanceof PlanarImg)
		{
			msg += " PlanarImg ";
			throw new Exception("Cannot send PlanarImg images yet.");
			//possibly add additional configuration hints to 'msg'
			//socket.send(msg.getBytes(), ZMQ.SNDMORE);

			//send metadata and voxel data afterwards
			//packAndSendPlusData(imgP, socket);
			//packAndSendPlanarImg((PlanarImg<T,?>)img, socket);
		}
		else
		if (img instanceof CellImg)
		{
			msg += " CellImg ";
			throw new Exception("Cannot send CellImg images yet.");
			//possibly add additional configuration hints to 'msg'
			//socket.send(msg.getBytes(), ZMQ.SNDMORE);

			//send metadata and voxel data afterwards
			//packAndSendPlusData(imgP, socket);
			//packAndSendCellImg((CellImg<T,?>)img, socket);
		}
		else
			throw new Exception("Cannot determine the type of image, cannot send it.");
	}


	@SuppressWarnings({ "unchecked", "rawtypes" })
	public
	ImgPlus<?> receiveAndUnpack(final String header, final ZMQ.Socket socket) throws Exception
	{
		StringTokenizer headerST = new StringTokenizer(header, " ");
		if (! headerST.nextToken().startsWith("v1"))
			throw new Exception("Unknown protocol, expecting protocol v1.");

		if (! headerST.nextToken().startsWith("dimNumber"))
			throw new Exception("Incorrect protocol, expecting dimNumber.");
		final int n = Integer.valueOf(headerST.nextToken());

		//fill the dimensionality data
		final int[] dims = new int[n];
		for (int i=0; i < n; ++i)
			dims[i] = Integer.valueOf(headerST.nextToken());

		final String typeStr = new String(headerST.nextToken());
		final String backendStr = new String(headerST.nextToken());

		//envelope/header message is (mostly) parsed,
		//start creating the output image of the appropriate type
		Img<? extends NativeType<?>> img = null;

		//create appropriate type and image variables
		if (typeStr.startsWith("ByteType"))
		{
			if (backendStr.startsWith("ArrayImg"))
				img = new ArrayImgFactory<ByteType>().create(dims, new ByteType());
			else
			if (backendStr.startsWith("PlanarImg"))
				img = new PlanarImgFactory<ByteType>().create(dims, new ByteType());
			else
			if (backendStr.startsWith("CellImg"))
				img = new CellImgFactory<ByteType>().create(dims, new ByteType());
		}
		else
		if (typeStr.startsWith("UnsignedByteType"))
		{
			if (backendStr.startsWith("ArrayImg"))
				img = new ArrayImgFactory<UnsignedByteType>().create(dims, new UnsignedByteType());
			else
			if (backendStr.startsWith("PlanarImg"))
				img = new PlanarImgFactory<UnsignedByteType>().create(dims, new UnsignedByteType());
			else
			if (backendStr.startsWith("CellImg"))
				img = new CellImgFactory<UnsignedByteType>().create(dims, new UnsignedByteType());
		}
		else
		if (typeStr.startsWith("ShortType"))
		{
			if (backendStr.startsWith("ArrayImg"))
				img = new ArrayImgFactory<ShortType>().create(dims, new ShortType());
			else
			if (backendStr.startsWith("PlanarImg"))
				img = new PlanarImgFactory<ShortType>().create(dims, new ShortType());
			else
			if (backendStr.startsWith("CellImg"))
				img = new CellImgFactory<ShortType>().create(dims, new ShortType());
		}
		else
		if (typeStr.startsWith("UnsignedShortType"))
		{
			if (backendStr.startsWith("ArrayImg"))
				img = new ArrayImgFactory<UnsignedShortType>().create(dims, new UnsignedShortType());
			else
			if (backendStr.startsWith("PlanarImg"))
				img = new PlanarImgFactory<UnsignedShortType>().create(dims, new UnsignedShortType());
			else
			if (backendStr.startsWith("CellImg"))
				img = new CellImgFactory<UnsignedShortType>().create(dims, new UnsignedShortType());
		}
		else
		if (typeStr.startsWith("FloatType"))
		{
			if (backendStr.startsWith("ArrayImg"))
				img = new ArrayImgFactory<FloatType>().create(dims, new FloatType());
			else
			if (backendStr.startsWith("PlanarImg"))
				img = new PlanarImgFactory<FloatType>().create(dims, new FloatType());
			else
			if (backendStr.startsWith("CellImg"))
				img = new CellImgFactory<FloatType>().create(dims, new FloatType());
		}
		else
		if (typeStr.startsWith("DoubleType"))
		{
			if (backendStr.startsWith("ArrayImg"))
				img = new ArrayImgFactory<DoubleType>().create(dims, new DoubleType());
			else
			if (backendStr.startsWith("PlanarImg"))
				img = new PlanarImgFactory<DoubleType>().create(dims, new DoubleType());
			else
			if (backendStr.startsWith("CellImg"))
				img = new CellImgFactory<DoubleType>().create(dims, new DoubleType());
		}
		else
			throw new Exception("Unsupported voxel type, sorry.");

		if (img == null)
			throw new Exception("Unsupported image backend type, sorry.");

		//the core Img is prepared, lets extend it with metadata and fill with voxel values afterwards
		//create the ImgPlus from it -- there is fortunately no deep coping
		ImgPlus<?> imgP = new ImgPlus<>(img);
		receiveAndUnpackPlusData((ImgPlus)imgP, socket);

		//populate with voxel data
		if (backendStr.startsWith("ArrayImg"))
		{
			receiveAndUnpackArrayImg((ArrayImg)img, socket);
		}
		else
		if (backendStr.startsWith("PlanarImg"))
		{
			//read possible additional configuration hints from 'header'
			//and fine-tune the img
			throw new Exception("Cannot receive PlanarImg images yet.");
			//receiveAndUnpackPlanarImg((PlanarImg)img, socket);
		}
		else
		if (backendStr.startsWith("CellImg"))
		{
			//read possible additional configuration hints from 'header'
			//and fine-tune the img
			throw new Exception("Cannot receive CellImg images yet.");
			//receiveAndUnpackCellImg((CellImg)img, socket);
		}
		else
			throw new Exception("Unsupported image backend type, sorry.");

		return imgP;
	}


	// -------- support for the transmission of the image metadata --------
	private
	void packAndSendPlusData(final ImgPlus<T> imgP, final ZMQ.Socket socket)
	{
		//TODO: use mPack because metadata are of various types (including Strings)
		//send single message with ZMQ.SNDMORE
	}

	private
	void receiveAndUnpackPlusData(final ImgPlus<T> imgP, final ZMQ.Socket socket)
	{
		//TODO: use mPack because metadata are of various types (including Strings)
		//read single message
	}


	// -------- support for the transmission of the payload/voxel data --------
	private
	void packAndSendArrayImg(final ArrayImg<T,? extends ArrayDataAccess<?>> img, final ZMQ.Socket socket) throws Exception
	{
		if (img.size() == 0)
			throw new Exception("Refusing to send an empty image...");

/*
		//create a buffer to hold the whole image (limit: img must not have more than 2GB of voxels)
		float[] array = new float[width * height];

		//create an image on top of this buffer
		Img<FloatType> wrappedArray = ArrayImgs.floats(array, width, height);

		//copy any image (that fits to the limit) to this image
		//and the voxel data will be available in the 'array' -- ready for transmission
		copy(img, wrappedArray);
*/

		switch (typeToTypeID(img.firstElement()))
		{
		case 1: //ByteType
		case 2: //UnsignedByteType
			{
			final byte[] data = (byte[])img.update(null).getCurrentStorageArray();
			packAndSendBytes(data, socket);
			}
			break;
		case 3: //ShortType
		case 4: //UnsignedShortType
			{
			final short[] data = (short[])img.update(null).getCurrentStorageArray();
			packAndSendShorts(data, socket);
			}
			break;
		case 5: //FloatType
			{
			final float[] data = (float[])img.update(null).getCurrentStorageArray();
			packAndSendFloats(data, socket);
			}
			break;
		case 6: //DoubleType
			{
			final double[] data = (double[])img.update(null).getCurrentStorageArray();
			packAndSendDoubles(data, socket);
			}
			break;
		default:
			throw new Exception("Unsupported voxel type, sorry.");
		}
	}

	private
	void receiveAndUnpackArrayImg(final ArrayImg<T,? extends ArrayDataAccess<?>> img, final ZMQ.Socket socket) throws Exception
	{
		if (img.size() == 0)
			throw new Exception("Refusing to receive an empty image...");

		switch (typeToTypeID(img.firstElement()))
		{
		case 1: //ByteType
		case 2: //UnsignedByteType
			{
			final byte[] data = (byte[])img.update(null).getCurrentStorageArray();
			receiveAndUnpackBytes(data, socket);
			}
			break;
		case 3: //ShortType
		case 4: //UnsignedShortType
			{
			final short[] data = (short[])img.update(null).getCurrentStorageArray();
			receiveAndUnpackShorts(data, socket);
			}
			break;
		case 5: //FloatType
			{
			final float[] data = (float[])img.update(null).getCurrentStorageArray();
			receiveAndUnpackFloats(data, socket);
			}
			break;
		case 6: //DoubleType
			{
			final double[] data = (double[])img.update(null).getCurrentStorageArray();
			receiveAndUnpackDoubles(data, socket);
			}
			break;
		default:
			throw new Exception("Unsupported voxel type, sorry.");
		}
	}

	private
	void packAndSendPlanarImg(final PlanarImg<T,? extends ArrayDataAccess<?>> img, final ZMQ.Socket socket) throws Exception
	{
		if (img.size() == 0)
			throw new Exception("Refusing to send an empty image...");

		for (int slice = 0; slice < img.numSlices(); ++slice)
		{
			img.getPlane(slice).getCurrentStorageArray();

		}
	}

	/**
	 * This one checks periodically (until timeout period) if
	 * there is some some incoming data reported on the socket.
	 * It finishes "nicely" if there is some, or finishes
	 * with an expection complaining about timeout.
	 */
	private
	void waitForVoxels(final ZMQ.Socket socket) throws Exception
	{
		final int timeOut = 20; //user param later!

		int timeWaited = 0;
		while (timeWaited < timeOut && !socket.hasReceiveMore())
		{
			//if nothing found, wait a while before another checking attempt
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			++timeWaited;
		}

		if (!socket.hasReceiveMore())
			throw new Exception("Time out for incomming voxel data.");
	}


	// -------- basic types storage vs. ByteType un/packagers --------
	private
	void packAndSendBytes(final byte[] data, final ZMQ.Socket socket)
	{
		final ByteBuffer buf = ByteBuffer.allocateDirect(data.length);
		buf.put(data);
		buf.rewind();
		socket.sendByteBuffer(buf, 0);
	}

	private
	void packAndSendShorts(final short[] data, final ZMQ.Socket socket)
	{
		//the data array might be as much as twice longer than what a byte[] array can store,
		//we have to copy half by half (each is up to byte[] array max capacity)
		//
		//but we keep addressing in the units of shorts :(
		//while in bytes the length of the data is always perfectly divisible by two,
		//it might not be the case in units of shorts
		final int TypeSize = 2;
		final int firstBlockLen = data.length/TypeSize + (data.length%TypeSize != 0? 1 : 0);
		final int lastBlockLen = data.length - (TypeSize-1)*firstBlockLen;
		//NB: firstBlockLen >= lastBlockLen

		final ByteBuffer buf = ByteBuffer.allocateDirect(TypeSize*firstBlockLen);
		buf.asShortBuffer().put(data, 0, firstBlockLen);
		buf.rewind();
		socket.sendByteBuffer(buf, (lastBlockLen > 0? ZMQ.SNDMORE : 0));

		if (lastBlockLen > 0)
		{
			buf.limit(TypeSize*lastBlockLen);
			buf.rewind();
			buf.asShortBuffer().put(data, firstBlockLen, lastBlockLen);
			buf.rewind();
			socket.sendByteBuffer(buf, 0);
		}
	}

	private
	void packAndSendFloats(final float[] data, final ZMQ.Socket socket)
	{
		final int TypeSize = 4;

		if (data.length < 1024)
		{
			//array that is short enough to be hosted entirely with byte[] array,
			//will be sent in one shot
			//NB: the else branch below cannot handle when data.length < 3,
			//    and why to split the short arrays anyways?
			final ByteBuffer buf = ByteBuffer.allocateDirect(TypeSize*data.length);
			buf.asFloatBuffer().put(data, 0, data.length);
			buf.rewind();
			socket.sendByteBuffer(buf, 0);
		}
		else
		{
			//float array, when seen as byte array, may exceed byte array's max length
			final int firstBlocksLen = data.length/TypeSize + (data.length%TypeSize != 0? 1 : 0);
			final int lastBlockLen = data.length - (TypeSize-1)*firstBlocksLen;

			final ByteBuffer buf = ByteBuffer.allocateDirect(TypeSize*firstBlocksLen);
			for (int p=0; p < (TypeSize-1); ++p)
			{
				//buf.rewind(); -- sendByteBuffer should not change the position as it works on a buf.duplicate()
				buf.asFloatBuffer().put(data, p*firstBlocksLen, firstBlocksLen);
				buf.rewind();
				socket.sendByteBuffer(buf, (lastBlockLen > 0 || p < TypeSize-2 ? ZMQ.SNDMORE : 0));
			}

			if (lastBlockLen > 0)
			{
				buf.limit(TypeSize*lastBlockLen);
				buf.rewind();
				buf.asFloatBuffer().put(data, (TypeSize-1)*firstBlocksLen, lastBlockLen);
				buf.rewind();
				socket.sendByteBuffer(buf, 0);
			}
		}
	}

	private
	void packAndSendDoubles(final double[] data, final ZMQ.Socket socket)
	{
		final int TypeSize = 8;

		if (data.length < 1024)
		{
			//array that is short enough to be hosted entirely with byte[] array,
			//will be sent in one shot
			//NB: the else branch below cannot handle when data.length < 7,
			//    and why to split the short arrays anyways?
			final ByteBuffer buf = ByteBuffer.allocateDirect(TypeSize*data.length);
			buf.asDoubleBuffer().put(data, 0, data.length);
			buf.rewind();
			socket.sendByteBuffer(buf, 0);
		}
		else
		{
			final int firstBlocksLen = data.length/TypeSize + (data.length%TypeSize != 0? 1 : 0);
			final int lastBlockLen = data.length - (TypeSize-1)*firstBlocksLen;

			final ByteBuffer buf = ByteBuffer.allocateDirect(TypeSize*firstBlocksLen);
			for (int p=0; p < (TypeSize-1); ++p)
			{
				//buf.rewind(); -- sendByteBuffer should not change the position as it works on a buf.duplicate()
				buf.asDoubleBuffer().put(data, p*firstBlocksLen, firstBlocksLen);
				buf.rewind();
				socket.sendByteBuffer(buf, (lastBlockLen > 0 || p < TypeSize-2 ? ZMQ.SNDMORE : 0));
			}

			if (lastBlockLen > 0)
			{
				buf.limit(TypeSize*lastBlockLen);
				buf.rewind();
				buf.asDoubleBuffer().put(data, (TypeSize-1)*firstBlocksLen, lastBlockLen);
				buf.rewind();
				socket.sendByteBuffer(buf, 0);
			}
		}
	}

	private
	void receiveAndUnpackBytes(final byte[] data, final ZMQ.Socket socket) throws Exception
	{
		final ByteBuffer buf = ByteBuffer.allocateDirect(data.length);
		//are there any further messages pending?
		waitForVoxels(socket);
		socket.recvByteBuffer(buf, 0);
		buf.rewind();
		buf.get(data);
	}

	private
	void receiveAndUnpackShorts(final short[] data, final ZMQ.Socket socket) throws Exception
	{
		//the data array might be as much as twice longer than what a byte[] array can store,
		//we have to copy half by half (each is up to byte[] array max capacity)
		//
		//but we keep addressing in the units of shorts :(
		//while in bytes the length of the data is always perfectly divisible by two,
		//it might not be the case in units of shorts
		final int TypeSize = 2;
		final int firstBlockLen = data.length/TypeSize + (data.length%TypeSize != 0? 1 : 0);
		final int lastBlockLen = data.length - (TypeSize-1)*firstBlockLen;
		//NB: firstBlockLen >= lastBlockLen

		final ByteBuffer buf = ByteBuffer.allocateDirect(TypeSize*firstBlockLen);
		waitForVoxels(socket);
		socket.recvByteBuffer(buf, 0); //blocking read since we got over waitForVoxels()
		buf.rewind();
		buf.asShortBuffer().get(data, 0, firstBlockLen);

		if (lastBlockLen > 0)
		{
			//make buffer ready for receiving the second part
			buf.limit(TypeSize*lastBlockLen);
			buf.rewind();

			//get the data
			waitForVoxels(socket);
			socket.recvByteBuffer(buf, 0);

			buf.rewind(); //recvByteBuffer() has changed the position!
			buf.asShortBuffer().get(data, firstBlockLen, lastBlockLen);
		}
	}

	private
	void receiveAndUnpackFloats(final float[] data, final ZMQ.Socket socket) throws Exception
	{
		final int TypeSize = 4;

		if (data.length < 1024)
		{
			//array that is short enough to be hosted entirely with byte[] array,
			//will be sent in one shot
			//NB: the else branch below cannot handle when data.length < 3,
			//    and why to split the short arrays anyways?
			final ByteBuffer buf = ByteBuffer.allocateDirect(TypeSize*data.length);
			waitForVoxels(socket);
			socket.recvByteBuffer(buf, 0);
			buf.rewind();
			buf.asFloatBuffer().get(data, 0, data.length);
		}
		else
		{
			//float array, when seen as byte array, may exceed byte array's max length
			final int firstBlocksLen = data.length/TypeSize + (data.length%TypeSize != 0? 1 : 0);
			final int lastBlockLen = data.length - (TypeSize-1)*firstBlocksLen;

			final ByteBuffer buf = ByteBuffer.allocateDirect(TypeSize*firstBlocksLen);
			for (int p=0; p < (TypeSize-1); ++p)
			{
				waitForVoxels(socket);
				socket.recvByteBuffer(buf, 0);
				buf.rewind();
				buf.asFloatBuffer().get(data, p*firstBlocksLen, firstBlocksLen);
				buf.rewind();
			}

			if (lastBlockLen > 0)
			{
				buf.limit(TypeSize*lastBlockLen);
				buf.rewind();
				waitForVoxels(socket);
				socket.recvByteBuffer(buf, 0);
				buf.rewind();
				buf.asFloatBuffer().get(data, (TypeSize-1)*firstBlocksLen, lastBlockLen);
			}
		}
	}

	private
	void receiveAndUnpackDoubles(final double[] data, final ZMQ.Socket socket) throws Exception
	{
		final int TypeSize = 8;

		if (data.length < 1024)
		{
			//array that is short enough to be hosted entirely with byte[] array,
			//will be sent in one shot
			//NB: the else branch below cannot handle when data.length < 7,
			//    and why to split the short arrays anyways?
			final ByteBuffer buf = ByteBuffer.allocateDirect(TypeSize*data.length);
			waitForVoxels(socket);
			socket.recvByteBuffer(buf, 0);
			buf.rewind();
			buf.asDoubleBuffer().get(data, 0, data.length);
		}
		else
		{
			final int firstBlocksLen = data.length/TypeSize + (data.length%TypeSize != 0? 1 : 0);
			final int lastBlockLen = data.length - (TypeSize-1)*firstBlocksLen;

			final ByteBuffer buf = ByteBuffer.allocateDirect(TypeSize*firstBlocksLen);
			for (int p=0; p < (TypeSize-1); ++p)
			{
				waitForVoxels(socket);
				socket.recvByteBuffer(buf, 0);
				buf.rewind();
				buf.asDoubleBuffer().get(data, p*firstBlocksLen, firstBlocksLen);
				buf.rewind();
			}

			if (lastBlockLen > 0)
			{
				buf.limit(TypeSize*lastBlockLen);
				buf.rewind();
				waitForVoxels(socket);
				socket.recvByteBuffer(buf, 0);
				buf.rewind();
				buf.asDoubleBuffer().get(data, (TypeSize-1)*firstBlocksLen, lastBlockLen);
			}
		}
	}


	// -------- the types war --------
	/*
	 * Keeps unwrapping the input image \e img
	 * until it gets to the underlying pure imglib2.Img.
	 */
	@SuppressWarnings("unchecked")
	private <Q> Img<Q> getUnderlyingImg(final Img<Q> img)
	{
		if (img instanceof Dataset)
			return (Img<Q>) getUnderlyingImg( ((Dataset)img).getImgPlus() );
		else if (img instanceof WrappedImg)
			return getUnderlyingImg( ((WrappedImg<Q>)img).getImg() );
		else
			return img;
	}

	/*
	 * Must be kept synchronized with typeToTypeID() !
	 */
	private
	String typeIDToString(final int ID) throws Exception
	{
		switch (ID)
		{
		case 1:
			return new String("ByteType");
		case 2:
			return new String("UnsignedByteType");
		case 3:
			return new String("ShortType");
		case 4:
			return new String("UnsignedShortType");
		case 5:
			return new String("FloatType");
		case 6:
			return new String("DoubleType");
		default:
			throw new Exception("Unsupported voxel type, sorry.");
		}
	}

	/*
	 * Must be kept synchronized with typeIDToString() !
	 */
	private
	int typeToTypeID(final T type) throws Exception
	{
		if (type instanceof ByteType) return 1;
		else
		if (type instanceof UnsignedByteType) return 2;
		else
		if (type instanceof ShortType) return 3;
		else
		if (type instanceof UnsignedShortType) return 4;
		else
		if (type instanceof FloatType) return 5;
		else
		if (type instanceof DoubleType) return 6;
		else
			throw new Exception("Unsupported voxel type, sorry.");
	}
}
