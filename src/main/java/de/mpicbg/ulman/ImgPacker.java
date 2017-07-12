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

import org.zeromq.ZMQ;

public class ImgPacker<T extends NativeType<T>>
{
	/**
	 *
	 */
	@SuppressWarnings("unchecked")
	public
	void packAndSend(final ImgPlus<T> imgP, final ZMQ.Socket socket)
	{
		//"buffer" for the first and human-readable payload:
		//protocol version
		String msg = new String("v1");

		//dimensionality data
		msg += " dimNumber " + imgP.numDimensions();
		for (int i=0; i < imgP.numDimensions(); ++i)
			msg += " " + imgP.dimension(i);

		//decipher the voxel type
		msg += " " + TypeId.of(imgP.firstElement());

		//check we can handle the storage model of this image,
		//and try to send everything (first the human readable payload, then raw voxel data)
		Img<T> img = getUnderlyingImg(imgP);
		if (img instanceof ArrayImg)
		{
			msg += " ArrayImg ";
			socket.send(msg.getBytes(), ZMQ.SNDMORE);

			//send metadata and voxel data afterwards
			packAndSendPlusData(imgP, socket);
			packAndSendArrayImg((ArrayImg<T,? extends ArrayDataAccess<?>>)img, socket);
		}
		else
		if (img instanceof PlanarImg)
		{
			//possibly add additional configuration hints to 'msg'
			msg += " PlanarImg "; //+((PlanarImg<T,?>)img).numSlices()+" ";
			//NB: The number of planes is deterministically given by the image size/dimensions.
			//    Hence, it is not necessary to provide such hint... 
			socket.send(msg.getBytes(), ZMQ.SNDMORE);

			//send metadata and voxel data afterwards
			packAndSendPlusData(imgP, socket);
			packAndSendPlanarImg((PlanarImg<T,? extends ArrayDataAccess<?>>)img, socket);
		}
		else
		if (img instanceof CellImg)
		{
			msg += " CellImg ";
			throw new RuntimeException("Cannot send CellImg images yet.");
			//possibly add additional configuration hints to 'msg'
			//socket.send(msg.getBytes(), ZMQ.SNDMORE);

			//send metadata and voxel data afterwards
			//packAndSendPlusData(imgP, socket);
			//packAndSendCellImg((CellImg<T,?>)img, socket);
		}
		else
			throw new RuntimeException("Cannot determine the type of image, cannot send it.");
	}


	@SuppressWarnings({ "unchecked", "rawtypes" })
	public
	ImgPlus<?> receiveAndUnpack(final String header, final ZMQ.Socket socket)
	{
		StringTokenizer headerST = new StringTokenizer(header, " ");
		if (! headerST.nextToken().startsWith("v1"))
			throw new RuntimeException("Unknown protocol, expecting protocol v1.");

		if (! headerST.nextToken().startsWith("dimNumber"))
			throw new RuntimeException("Incorrect protocol, expecting dimNumber.");
		final int n = Integer.valueOf(headerST.nextToken());

		//fill the dimensionality data
		final int[] dims = new int[n];
		for (int i=0; i < n; ++i)
			dims[i] = Integer.valueOf(headerST.nextToken());

		final String typeStr = new String(headerST.nextToken());
		final String backendStr = new String(headerST.nextToken());

		//envelope/header message is (mostly) parsed,
		//start creating the output image of the appropriate type
		Img<? extends NativeType<?>> img = createImg(dims, backendStr, createType(typeStr));

		if (img == null)
			throw new RuntimeException("Unsupported image backend type, sorry.");

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
			//final int Slices = Integer.valueOf(headerST.nextToken());
			//and fine-tune the img
			receiveAndUnpackPlanarImg((PlanarImg)img, socket);
		}
		else
		if (backendStr.startsWith("CellImg"))
		{
			//read possible additional configuration hints from 'header'
			//and fine-tune the img
			throw new RuntimeException("Cannot receive CellImg images yet.");
			//receiveAndUnpackCellImg((CellImg)img, socket);
		}
		else
			throw new RuntimeException("Unsupported image backend type, sorry.");

		return imgP;
	}

	@SuppressWarnings("rawtype") // use raw type because of insufficient support of reflexive types in java
	private NativeType createType(String typeStr) {
		for(TypeId id : TypeId.values())
			if(typeStr.startsWith(id.toString()))
				try {
					return id.aClass.newInstance();
				} catch (InstantiationException | IllegalAccessException e) {
					throw new RuntimeException(e);
				}
		throw new RuntimeException("Unsupported voxel type, sorry.");
	}

	private <T extends NativeType<T>> Img<T> createImg(int[] dims, String backendStr, T type) {
		if (backendStr.startsWith("ArrayImg"))
			return new ArrayImgFactory<T>().create(dims, type);
		if (backendStr.startsWith("PlanarImg"))
			return new PlanarImgFactory<T>().create(dims, type);
		if (backendStr.startsWith("CellImg"))
			return new CellImgFactory<T>().create(dims, type);
		throw new RuntimeException("Unsupported image backend type, sorry.");
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
	void packAndSendArrayImg(final ArrayImg<T,? extends ArrayDataAccess<?>> img, final ZMQ.Socket socket)
	{
		if (img.size() == 0)
			throw new RuntimeException("Refusing to send an empty image...");

/*
		//create a buffer to hold the whole image (limit: img must not have more than 2GB of voxels)
		float[] array = new float[width * height];

		//create an image on top of this buffer
		Img<FloatType> wrappedArray = ArrayImgs.floats(array, width, height);

		//copy any image (that fits to the limit) to this image
		//and the voxel data will be available in the 'array' -- ready for transmission
		copy(img, wrappedArray);
*/

		switch (TypeId.of(img.firstElement()))
		{
		case BYTE:
		case UNSIGNED_BYTE:
			{
			final byte[] data = (byte[])img.update(null).getCurrentStorageArray();
			PackAndSendHelper.packAndSendBytes(data, socket, false);
			}
			break;
		case SHORT:
		case UNSIGNED_SHORT:
			{
			final short[] data = (short[])img.update(null).getCurrentStorageArray();
			PackAndSendHelper.packAndSendShorts(data, socket, false);
			}
			break;
		case FLOAT:
			{
			final float[] data = (float[])img.update(null).getCurrentStorageArray();
			PackAndSendHelper.packAndSendFloats(data, socket, false);
			}
			break;
		case DOUBLE:
			{
			final double[] data = (double[])img.update(null).getCurrentStorageArray();
			PackAndSendHelper.packAndSendDoubles(data, socket, false);
			}
			break;
		default:
			throw new RuntimeException("Unsupported voxel type, sorry.");
		}
	}

	private
	void receiveAndUnpackArrayImg(final ArrayImg<T,? extends ArrayDataAccess<?>> img, final ZMQ.Socket socket)
	{
		if (img.size() == 0)
			throw new RuntimeException("Refusing to receive an empty image...");

		switch (TypeId.of(img.firstElement()))
		{
		case BYTE:
		case UNSIGNED_BYTE:
			{
			final byte[] data = (byte[])img.update(null).getCurrentStorageArray();
			ReceiveAndUnpackHelper.receiveAndUnpackBytes(data, socket);
			}
			break;
		case SHORT:
		case UNSIGNED_SHORT:
			{
			final short[] data = (short[])img.update(null).getCurrentStorageArray();
			ReceiveAndUnpackHelper.receiveAndUnpackShorts(data, socket);
			}
			break;
		case FLOAT:
			{
			final float[] data = (float[])img.update(null).getCurrentStorageArray();
			ReceiveAndUnpackHelper.receiveAndUnpackFloats(data, socket);
			}
			break;
		case DOUBLE:
			{
			final double[] data = (double[])img.update(null).getCurrentStorageArray();
			ReceiveAndUnpackHelper.receiveAndUnpackDoubles(data, socket);
			}
			break;
		default:
			throw new RuntimeException("Unsupported voxel type, sorry.");
		}
	}

	private
	void packAndSendPlanarImg(final PlanarImg<T,? extends ArrayDataAccess<?>> img, final ZMQ.Socket socket)
	{
		if (img.size() == 0)
			throw new RuntimeException("Refusing to send an empty image...");

		switch (TypeId.of(img.firstElement()))
		{
		case BYTE:
		case UNSIGNED_BYTE:
			for (int slice = 0; slice < img.numSlices()-1; ++slice)
			{
				final byte[] data = (byte[])img.getPlane(slice).getCurrentStorageArray();
				PackAndSendHelper.packAndSendBytes(data, socket, true);
			}
			{
				final byte[] data = (byte[])img.getPlane(img.numSlices()-1).getCurrentStorageArray();
				PackAndSendHelper.packAndSendBytes(data, socket, false);
			}
			break;
		case SHORT:
		case UNSIGNED_SHORT:
			for (int slice = 0; slice < img.numSlices()-1; ++slice)
			{
				final short[] data = (short[])img.getPlane(slice).getCurrentStorageArray();
				PackAndSendHelper.packAndSendShorts(data, socket, true);
			}
			{
				final short[] data = (short[])img.getPlane(img.numSlices()-1).getCurrentStorageArray();
				PackAndSendHelper.packAndSendShorts(data, socket, false);
			}
			break;
		case FLOAT:
			for (int slice = 0; slice < img.numSlices()-1; ++slice)
			{
				final float[] data = (float[])img.getPlane(slice).getCurrentStorageArray();
				PackAndSendHelper.packAndSendFloats(data, socket, true);
			}
			{
				final float[] data = (float[])img.getPlane(img.numSlices()-1).getCurrentStorageArray();
				PackAndSendHelper.packAndSendFloats(data, socket, false);
			}
			break;
		case DOUBLE:
			for (int slice = 0; slice < img.numSlices()-1; ++slice)
			{
				final double[] data = (double[])img.getPlane(slice).getCurrentStorageArray();
				PackAndSendHelper.packAndSendDoubles(data, socket, true);
			}
			{
				final double[] data = (double[])img.getPlane(img.numSlices()-1).getCurrentStorageArray();
				PackAndSendHelper.packAndSendDoubles(data, socket, false);
			}
			break;
		default:
			throw new RuntimeException("Unsupported voxel type, sorry.");
		}
	}

	private
	void receiveAndUnpackPlanarImg(final PlanarImg<T,? extends ArrayDataAccess<?>> img, final ZMQ.Socket socket)
	{
		if (img.size() == 0)
			throw new RuntimeException("Refusing to receive an empty image...");

		switch (TypeId.of(img.firstElement()))
		{
		case BYTE:
		case UNSIGNED_BYTE:
			for (int slice = 0; slice < img.numSlices()-1; ++slice)
			{
				final byte[] data = (byte[])img.getPlane(slice).getCurrentStorageArray();
				ReceiveAndUnpackHelper.receiveAndUnpackBytes(data, socket);
			}
			{
				final byte[] data = (byte[])img.getPlane(img.numSlices()-1).getCurrentStorageArray();
				ReceiveAndUnpackHelper.receiveAndUnpackBytes(data, socket);
			}
			break;
		case SHORT:
		case UNSIGNED_SHORT:
			for (int slice = 0; slice < img.numSlices()-1; ++slice)
			{
				final short[] data = (short[])img.getPlane(slice).getCurrentStorageArray();
				ReceiveAndUnpackHelper.receiveAndUnpackShorts(data, socket);
			}
			{
				final short[] data = (short[])img.getPlane(img.numSlices()-1).getCurrentStorageArray();
				ReceiveAndUnpackHelper.receiveAndUnpackShorts(data, socket);
			}
			break;
		case FLOAT:
			for (int slice = 0; slice < img.numSlices()-1; ++slice)
			{
				final float[] data = (float[])img.getPlane(slice).getCurrentStorageArray();
				ReceiveAndUnpackHelper.receiveAndUnpackFloats(data, socket);
			}
			{
				final float[] data = (float[])img.getPlane(img.numSlices()-1).getCurrentStorageArray();
				ReceiveAndUnpackHelper.receiveAndUnpackFloats(data, socket);
			}
			break;
		case DOUBLE:
			for (int slice = 0; slice < img.numSlices()-1; ++slice)
			{
				final double[] data = (double[])img.getPlane(slice).getCurrentStorageArray();
				ReceiveAndUnpackHelper.receiveAndUnpackDoubles(data, socket);
			}
			{
				final double[] data = (double[])img.getPlane(img.numSlices()-1).getCurrentStorageArray();
				ReceiveAndUnpackHelper.receiveAndUnpackDoubles(data, socket);
			}
			break;
		default:
			throw new RuntimeException("Unsupported voxel type, sorry.");
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

	private enum TypeId {
		BYTE(ByteType.class),
		UNSIGNED_BYTE(UnsignedByteType.class),
		SHORT(ShortType.class),
		UNSIGNED_SHORT(UnsignedShortType.class),
		FLOAT(FloatType.class),
		DOUBLE(DoubleType.class);

		private final Class<? extends NativeType<?>> aClass;

		TypeId(Class<? extends NativeType<?>> aClass) {
			this.aClass = aClass;
		}

		@Override
		public String toString() {
			return aClass.getSimpleName();
		}

		public static TypeId of(final Object type) {
			for(TypeId id : TypeId.values())
				if(id.aClass.isInstance(type))
					return id;
			throw new IllegalArgumentException("Unsupported voxel type, sorry.");
		}

	}
}
