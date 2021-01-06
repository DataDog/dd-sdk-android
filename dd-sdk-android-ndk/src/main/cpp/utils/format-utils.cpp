#include <string>
#include <stdexcept>

namespace strformat {

    template<typename... Args>
    std::string format(std::string formatter, Args... args) {
        using namespace std;
        const auto formatter_value = formatter.c_str();
        // first we compute the size of the buffer required to format this string
        // we will add 1 at the end for the end of string /0 character
        const size_t size = snprintf(nullptr, 0, formatter_value, args...) + 1;
        if (size <= 0) {
            throw runtime_error("Error during string formatting.");
        }
        unique_ptr<char[]> buffer_pointer(new char[size]);
        unique_ptr < char[], default_delete < char[] >> ::pointer
        buffer = buffer_pointer.get();
        snprintf(buffer, size, formatter_value, args...);
        return string(buffer, buffer + size - 1);
    }

}
