import java.util.regex.Pattern;

public class Utils
{
	private static final Pattern INTEGER_REGEX = Pattern.compile("-?\\d+");

	/**
	 * Modulus function with the desired behaviour for aa < 0.
	 */
	static double mod(double aa, double bb)
	{
		if (aa >= 0)
		{
			return aa % bb;
		}
		else
		{
			if ((-aa % bb) != 0)
			{
				return bb - (-aa % bb);
			}
			else
			{
				return 0d;
			}
		}
	}

	/**
	 * Modulus function with the desired behaviour for aa < 0.
	 */
	static int mod(int aa, int bb)
	{
		if (aa >= 0)
		{
			return aa % bb;
		}
		else
		{
			if ((-aa % bb) != 0)
			{
				return bb - (-aa % bb);
			}
			else
			{
				return 0;
			}
		}
	}

	/**
	 * Return true if the input String can be parsed as an integer number, by
	 * matching it to the <i>-?//d+</i> regex.
	 */
	public static boolean isInteger(String numericString)
	{
		return INTEGER_REGEX.matcher(numericString).matches();
	}
}